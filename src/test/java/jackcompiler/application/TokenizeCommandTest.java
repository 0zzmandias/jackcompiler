package jackcompiler.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.LexicalException;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TokenizeCommandTest {

  @TempDir Path tempDir;

  private final TokenizeCommand command = new TokenizeCommand();

  @Test
  void tokenizesSingleSourceIntoCustomDirectory() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = tempDir.resolve("generated");

    List<Path> outputs = command.execute(source, Optional.of(outputDirectory));

    Path output = outputDirectory.resolve("MainT.xml");

    assertEquals(List.of(output), outputs);
    assertEquals(
        "<tokens>\n"
            + "<keyword> class </keyword>\n"
            + "<identifier> Main </identifier>\n"
            + "<symbol> { </symbol>\n"
            + "<symbol> } </symbol>\n"
            + "</tokens>\n",
        Files.readString(output));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void usesDefaultGeneratedTokensDirectoryAndAllowsRegeneration() throws Exception {
    String stem = "Integration_" + tempDir.getFileName();
    Path source = write(stem + ".jack", "class First {}");
    Path expected =
        Path.of("target", "generated-tokens", stem + "T.xml").toAbsolutePath().normalize();

    try {
      List<Path> first = command.execute(source, Optional.empty());

      assertEquals(List.of(expected), first);
      assertTrue(Files.readString(expected).contains("<identifier> First </identifier>"));

      Files.writeString(source, "class Second {}", StandardCharsets.UTF_8);

      List<Path> second = command.execute(source, Optional.empty());

      assertEquals(List.of(expected), second);
      String xml = Files.readString(expected);
      assertTrue(xml.contains("<identifier> Second </identifier>"));
      assertFalse(xml.contains("<identifier> First </identifier>"));
    } finally {
      Files.deleteIfExists(expected);
    }
  }

  @Test
  void returnsAbsoluteNormalizedOutputPaths() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path relative = Path.of("").toAbsolutePath().relativize(source);

    List<Path> outputs = command.execute(relative, Optional.of(tempDir.resolve("out")));

    assertEquals(List.of(tempDir.resolve("out/MainT.xml")), outputs);
    assertTrue(outputs.get(0).isAbsolute());
  }

  @Test
  void processesDirectoryInDeterministicOrder() throws Exception {
    write("Zebra.jack", "class Zebra {}");
    write("Alpha.jack", "class Alpha {}");
    write("Main.jack", "class Main {}");

    Path outputDirectory = tempDir.resolve("out");
    List<Path> outputs = command.execute(tempDir, Optional.of(outputDirectory));

    assertEquals(
        List.of(
            outputDirectory.resolve("AlphaT.xml"),
            outputDirectory.resolve("MainT.xml"),
            outputDirectory.resolve("ZebraT.xml")),
        outputs);
  }

  @Test
  void ignoresUnrelatedFilesAndNestedDirectories() throws Exception {
    write("Main.jack", "class Main {}");
    write("notes.txt", "notes");
    write("Other.JACK", "class Other {}");

    Path nested = Files.createDirectory(tempDir.resolve("nested"));
    Files.writeString(nested.resolve("Nested.jack"), "class Nested {}");

    Path outputDirectory = tempDir.resolve("out");
    List<Path> outputs = command.execute(tempDir, Optional.of(outputDirectory));

    assertEquals(List.of(outputDirectory.resolve("MainT.xml")), outputs);
    assertFalse(Files.exists(outputDirectory.resolve("NestedT.xml")));
    assertFalse(Files.exists(outputDirectory.resolve("OtherT.xml")));
  }

  @Test
  void preservesUnicodeAndXmlEscaping() throws Exception {
    Path source = write("Olá.jack", "do Output.printString(\"Olá 😀 <&>\");");

    Path output = command.execute(source, Optional.of(tempDir.resolve("out"))).get(0);

    assertTrue(
        Files.readString(output)
            .contains("<stringConstant> Olá 😀 &lt;&amp;&gt; </stringConstant>"));
  }

  @Test
  void preservesSourceBytesAndLineEndings() throws Exception {
    Path source = write("Main.jack", "class Main {\r\n  // Olá 😀\r\n}\r\n");
    byte[] before = Files.readAllBytes(source);

    command.execute(source, Optional.of(tempDir.resolve("out")));

    assertArrayEquals(before, Files.readAllBytes(source));
  }

  @Test
  void serializesEmptySource() throws Exception {
    Path source = write("Empty.jack", "");

    Path output = command.execute(source, Optional.of(tempDir.resolve("out"))).get(0);

    assertEquals("<tokens>\n</tokens>\n", Files.readString(output));
  }

  @Test
  void serializesCommentOnlySource() throws Exception {
    Path source = write("Comments.jack", "// comment\n/* block */");

    Path output = command.execute(source, Optional.of(tempDir.resolve("out"))).get(0);

    assertEquals("<tokens>\n</tokens>\n", Files.readString(output));
  }

  @Test
  void returnsImmutableOutputList() throws Exception {
    Path source = write("Main.jack", "class Main {}");

    List<Path> outputs = command.execute(source, Optional.of(tempDir.resolve("out")));

    assertThrows(
        UnsupportedOperationException.class, () -> outputs.add(tempDir.resolve("AnotherT.xml")));
  }

  @Test
  void customDirectoryRejectsExistingOutputWithoutChangingIt() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Path destination = Files.writeString(outputDirectory.resolve("MainT.xml"), "original");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(outputDirectory)));

    assertEquals(source, error.sourceFile());
    assertInstanceOf(FileAlreadyExistsException.class, error.getCause());
    assertEquals("original", Files.readString(destination));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void customDirectoryRejectsCollisionOnRepeatedExecution() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = tempDir.resolve("out");

    List<Path> first = command.execute(source, Optional.of(outputDirectory));
    String original = Files.readString(first.get(0));

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(outputDirectory)));

    assertInstanceOf(FileAlreadyExistsException.class, error.getCause());
    assertEquals(original, Files.readString(first.get(0)));
  }

  @Test
  void customOutputDirectoryMayBeCreatedAutomatically() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = tempDir.resolve("missing").resolve("nested");

    command.execute(source, Optional.of(outputDirectory));

    assertTrue(Files.isRegularFile(outputDirectory.resolve("MainT.xml")));
  }

  @Test
  void preservesOfficialReferenceThroughCollisionProtection() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path reference = Files.writeString(tempDir.resolve("MainT.xml"), "official reference");
    byte[] before = Files.readAllBytes(reference);

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(source, Optional.of(tempDir)));

    assertInstanceOf(FileAlreadyExistsException.class, error.getCause());
    assertArrayEquals(before, Files.readAllBytes(reference));
  }

  @Test
  void rejectsSymbolicLinkOutputWithoutTouchingItsTarget() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Path protectedFile = Files.writeString(tempDir.resolve("protected.xml"), "protected");

    Files.createSymbolicLink(outputDirectory.resolve("MainT.xml"), protectedFile);

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(outputDirectory)));

    assertEquals(source, error.sourceFile());
    assertInstanceOf(IOException.class, error.getCause());
    assertEquals("protected", Files.readString(protectedFile));
  }

  @Test
  void rejectsOutputAliasToSource() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Path destination = outputDirectory.resolve("MainT.xml");

    Files.createLink(destination, source);

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(outputDirectory)));

    assertTrue(error.getCause().getMessage().contains("aliases the source"));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void rejectsOutputInsideProtectedFixtureDirectory() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory =
        tempDir.resolve("src").resolve("test").resolve("resources").resolve("reference");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(outputDirectory)));

    assertInstanceOf(IOException.class, error.getCause());
    assertFalse(Files.exists(outputDirectory.resolve("MainT.xml")));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void wrapsMissingInputWithRequestedPath() {
    Path missing = tempDir.resolve("Missing.jack");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(missing, Optional.empty()));

    assertEquals(missing, error.sourceFile());
    assertInstanceOf(NoSuchFileException.class, error.getCause());
  }

  @Test
  void rejectsDirectoryWithoutJackSources() {
    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(tempDir, Optional.of(tempDir.resolve("out"))));

    assertEquals(tempDir, error.sourceFile());
    assertInstanceOf(IOException.class, error.getCause());
    assertTrue(error.getMessage().contains("No .jack files"));
  }

  @Test
  void wrapsUnsupportedSourceExtension() throws Exception {
    Path source = write("Main.txt", "class Main {}");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(tempDir.resolve("out"))));

    assertEquals(source, error.sourceFile());
    assertInstanceOf(IllegalArgumentException.class, error.getCause());
  }

  @Test
  void rejectsMalformedUtf8AndPreservesSourceBytes() throws Exception {
    Path source = tempDir.resolve("Malformed.jack");
    byte[] malformed = {(byte) 0xC3, (byte) 0x28};
    Files.write(source, malformed);

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(tempDir.resolve("out"))));

    assertEquals(source, error.sourceFile());
    assertInstanceOf(MalformedInputException.class, error.getCause());
    assertArrayEquals(malformed, Files.readAllBytes(source));
    assertFalse(Files.exists(tempDir.resolve("out/MalformedT.xml")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"@", "let x = 32768;", "let x = \"unfinished", "/* unfinished"})
  void lexicalFailureDoesNotPublishOutput(String invalid) throws Exception {
    Path source = write("Invalid.jack", invalid);
    Path outputDirectory = tempDir.resolve("out");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(outputDirectory)));

    assertEquals(source, error.sourceFile());

    LexicalException lexical = assertInstanceOf(LexicalException.class, error.getCause());

    assertNotNull(lexical.position());
    assertFalse(Files.exists(outputDirectory.resolve("InvalidT.xml")));
    assertEquals(invalid, Files.readString(source));
  }

  @Test
  void lexicalFailurePreservesOriginalLineAndColumn() throws Exception {
    Path source = write("Invalid.jack", "class Main {}\n@");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(tempDir.resolve("out"))));

    LexicalException cause = assertInstanceOf(LexicalException.class, error.getCause());

    assertEquals(2, cause.position().line());
    assertEquals(1, cause.position().column());
  }

  @Test
  void serializationFailureDoesNotPublishOutput() throws Exception {
    Path source = write("Invalid.jack", "let s = \"a\u0001b\";");
    Path outputDirectory = tempDir.resolve("out");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(outputDirectory)));

    assertEquals(source, error.sourceFile());
    assertInstanceOf(IllegalArgumentException.class, error.getCause());
    assertTrue(error.getCause().getMessage().contains("Invalid XML character"));
    assertFalse(Files.exists(outputDirectory.resolve("InvalidT.xml")));
  }

  @Test
  void failsFastAndPreservesPreviouslyCompletedOutputs() throws Exception {
    write("A.jack", "class A {}");
    Path invalid = write("B.jack", "@");
    write("C.jack", "class C {}");

    Path outputDirectory = tempDir.resolve("out");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(tempDir, Optional.of(outputDirectory)));

    assertEquals(invalid, error.sourceFile());
    assertInstanceOf(LexicalException.class, error.getCause());

    assertTrue(Files.isRegularFile(outputDirectory.resolve("AT.xml")));
    assertFalse(Files.exists(outputDirectory.resolve("BT.xml")));
    assertFalse(Files.exists(outputDirectory.resolve("CT.xml")));
  }

  @Test
  void failsFastOnOutputCollisionWithoutOverwritingPriorFiles() throws Exception {
    write("A.jack", "class A {}");
    Path sourceB = write("B.jack", "class B {}");
    write("C.jack", "class C {}");

    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Path existing = Files.writeString(outputDirectory.resolve("BT.xml"), "existing");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(tempDir, Optional.of(outputDirectory)));

    assertEquals(sourceB, error.sourceFile());
    assertInstanceOf(FileAlreadyExistsException.class, error.getCause());

    assertTrue(Files.isRegularFile(outputDirectory.resolve("AT.xml")));
    assertEquals("existing", Files.readString(existing));
    assertFalse(Files.exists(outputDirectory.resolve("CT.xml")));
  }

  @Test
  void doesNotLeaveTemporaryFilesAfterCollision() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Files.writeString(outputDirectory.resolve("MainT.xml"), "existing");

    assertThrows(
        SourceProcessingException.class,
        () -> command.execute(source, Optional.of(outputDirectory)));

    try (var entries = Files.list(outputDirectory)) {
      assertEquals(
          0L,
          entries
              .filter(path -> path.getFileName().toString().startsWith(".jackcompiler-"))
              .count());
    }
  }

  @Test
  void rejectsNullInputPath() {
    assertThrows(NullPointerException.class, () -> command.execute(null, Optional.empty()));
  }

  @Test
  void rejectsNullOutputDirectoryOptional() {
    assertThrows(
        NullPointerException.class, () -> command.execute(tempDir.resolve("Main.jack"), null));
  }

  private Path write(String name, String content) throws IOException {
    return Files.writeString(tempDir.resolve(name), content, StandardCharsets.UTF_8);
  }
}
