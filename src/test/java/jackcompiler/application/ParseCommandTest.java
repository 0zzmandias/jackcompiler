package jackcompiler.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.LexicalException;
import jackcompiler.parser.SyntaxException;
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

class ParseCommandTest {

  @TempDir Path tempDir;

  private final ParseCommand command = new ParseCommand();

  @Test
  void parsesSingleSourceIntoCustomDirectory() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = tempDir.resolve("generated");

    List<Path> outputs = command.execute(source, Optional.of(outputDirectory));

    Path output = outputDirectory.resolve("MainP.xml");

    assertEquals(List.of(output), outputs);
    assertEquals(
        "<class>\n"
            + "  <keyword> class </keyword>\n"
            + "  <identifier> Main </identifier>\n"
            + "  <symbol> { </symbol>\n"
            + "  <symbol> } </symbol>\n"
            + "</class>\n",
        Files.readString(output, StandardCharsets.UTF_8));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void integratesLexerParserAndSyntaxXmlWriter() throws Exception {
    String sourceText =
        "class Main {"
            + " field int count;"
            + " function void run(int x) {"
            + " var int y;"
            + " let y = x + 1;"
            + " if (y > 0) { do Output.printInt(y); }"
            + " else { while (y) { let y = y - 1; } }"
            + " return;"
            + " }"
            + "}";

    Path source = write("Main.jack", sourceText);
    Path outputDirectory = tempDir.resolve("generated");

    List<Path> outputs = command.execute(source, Optional.of(outputDirectory));

    String xml = Files.readString(outputs.get(0), StandardCharsets.UTF_8);

    assertTrue(xml.startsWith("<class>\n"));
    assertTrue(xml.contains("<classVarDec>"));
    assertTrue(xml.contains("<subroutineDec>"));
    assertTrue(xml.contains("<parameterList>"));
    assertTrue(xml.contains("<varDec>"));
    assertTrue(xml.contains("<letStatement>"));
    assertTrue(xml.contains("<ifStatement>"));
    assertTrue(xml.contains("<whileStatement>"));
    assertTrue(xml.contains("<doStatement>"));
    assertTrue(xml.contains("<returnStatement>"));
    assertTrue(xml.contains("<symbol> &gt; </symbol>"));
    assertTrue(xml.endsWith("</class>\n"));
    assertFalse(xml.contains("<tokens>"));
    assertEquals(sourceText, Files.readString(source));
  }

  @Test
  void preservesSourceStringWhitespaceAndEscapesXml() throws Exception {
    Path source =
        write(
            "Main.jack",
            "class Main {"
                + " function void run() {"
                + " do Output.printString(\" a&<>b \");"
                + " return;"
                + " }"
                + "}");

    Path output = command.execute(source, Optional.of(tempDir.resolve("out"))).get(0);
    String xml = Files.readString(output, StandardCharsets.UTF_8);

    assertTrue(xml.contains("<stringConstant>  a&amp;&lt;&gt;b  </stringConstant>"));
    assertEquals("MainP.xml", output.getFileName().toString());
  }

  @Test
  void preservesCommentsAndNormalizesXmlLineEndings() throws Exception {
    Path source =
        write(
            "Main.jack",
            "// heading\r\n"
                + "class Main {\r\n"
                + "  function void run() { /* body */\r\n"
                + "    return;\r\n"
                + "  }\r\n"
                + "}\r\n");

    Path output = command.execute(source, Optional.of(tempDir.resolve("out"))).get(0);
    String xml = Files.readString(output, StandardCharsets.UTF_8);

    assertTrue(xml.startsWith("<class>\n"));
    assertFalse(xml.contains("\r"));
    assertFalse(xml.contains("heading"));
    assertFalse(xml.contains("body"));
    assertTrue(xml.contains("<returnStatement>"));
  }

  @Test
  void generatesSyntaxXmlWithoutGeneratingTokenXml() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = tempDir.resolve("generated");

    command.execute(source, Optional.of(outputDirectory));

    assertTrue(Files.isRegularFile(outputDirectory.resolve("MainP.xml")));
    assertFalse(Files.exists(outputDirectory.resolve("MainT.xml")));
  }

  @Test
  void usesDefaultParserDirectoryAndAllowsRegeneration() throws Exception {
    String stem = "ParserIntegration_" + tempDir.getFileName();
    Path source = write(stem + ".jack", "class First {}");
    Path expected =
        Path.of("target", "generated-parser", stem + "P.xml").toAbsolutePath().normalize();

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
  void preservesExistingDefaultOutputIfNewSyntaxAnalysisFails() throws Exception {
    String stem = "ParserSyntaxFailure_" + tempDir.getFileName();
    Path source = write(stem + ".jack", "class First {}");
    Path expected =
        Path.of("target", "generated-parser", stem + "P.xml").toAbsolutePath().normalize();

    try {
      command.execute(source, Optional.empty());
      String originalXml = Files.readString(expected);

      Files.writeString(source, "class Broken {", StandardCharsets.UTF_8);

      SourceProcessingException error =
          assertThrows(
              SourceProcessingException.class, () -> command.execute(source, Optional.empty()));

      assertInstanceOf(SyntaxException.class, error.getCause());
      assertEquals(originalXml, Files.readString(expected));
    } finally {
      Files.deleteIfExists(expected);
    }
  }

  @Test
  void preservesExistingDefaultOutputIfNewLexicalAnalysisFails() throws Exception {
    String stem = "ParserLexicalFailure_" + tempDir.getFileName();
    Path source = write(stem + ".jack", "class First {}");
    Path expected =
        Path.of("target", "generated-parser", stem + "P.xml").toAbsolutePath().normalize();

    try {
      command.execute(source, Optional.empty());
      String originalXml = Files.readString(expected);

      Files.writeString(source, "class Broken { @ }", StandardCharsets.UTF_8);

      SourceProcessingException error =
          assertThrows(
              SourceProcessingException.class, () -> command.execute(source, Optional.empty()));

      assertInstanceOf(LexicalException.class, error.getCause());
      assertEquals(originalXml, Files.readString(expected));
    } finally {
      Files.deleteIfExists(expected);
    }
  }

  @Test
  void rejectsCustomOutputCollisionWithoutReplacingExistingContent() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Path existing = Files.writeString(outputDirectory.resolve("MainP.xml"), "protected");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(outputDirectory)));

    assertEquals(source, error.sourceFile());
    assertInstanceOf(FileAlreadyExistsException.class, error.getCause());
    assertEquals("protected", Files.readString(existing));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void rejectsRegenerationInCustomDirectory() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = tempDir.resolve("out");

    command.execute(source, Optional.of(outputDirectory));
    String originalXml = Files.readString(outputDirectory.resolve("MainP.xml"));

    Files.writeString(source, "class Changed {}", StandardCharsets.UTF_8);

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(outputDirectory)));

    assertInstanceOf(FileAlreadyExistsException.class, error.getCause());
    assertEquals(originalXml, Files.readString(outputDirectory.resolve("MainP.xml")));
  }

  @Test
  void returnsAbsoluteNormalizedPaths() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path directory = tempDir.resolve("parent").resolve("..").resolve("generated");

    List<Path> outputs = command.execute(source, Optional.of(directory));

    assertEquals(List.of(tempDir.resolve("generated").resolve("MainP.xml")), outputs);
    assertTrue(outputs.get(0).isAbsolute());
  }

  @Test
  void returnsImmutableOutputList() throws Exception {
    Path source = write("Main.jack", "class Main {}");

    List<Path> outputs = command.execute(source, Optional.of(tempDir.resolve("out")));

    assertEquals(1, outputs.size());
    assertThrows(UnsupportedOperationException.class, () -> outputs.add(source));
  }

  @Test
  void parsesDirectoryInDeterministicFilenameOrder() throws Exception {
    write("Z.jack", "class Z {}");
    write("A.jack", "class A {}");
    write("Middle.jack", "class Middle {}");

    Path outputDirectory = tempDir.resolve("out");

    List<Path> outputs = command.execute(tempDir, Optional.of(outputDirectory));

    assertEquals(
        List.of(
            outputDirectory.resolve("AP.xml"),
            outputDirectory.resolve("MiddleP.xml"),
            outputDirectory.resolve("ZP.xml")),
        outputs);

    assertTrue(Files.readString(outputs.get(0)).contains("<identifier> A </identifier>"));
    assertTrue(Files.readString(outputs.get(1)).contains("<identifier> Middle </identifier>"));
    assertTrue(Files.readString(outputs.get(2)).contains("<identifier> Z </identifier>"));
  }

  @Test
  void doesNotRecurseIntoSubdirectories() throws Exception {
    write("Main.jack", "class Main {}");
    Path nested = Files.createDirectory(tempDir.resolve("nested"));
    Files.writeString(nested.resolve("Hidden.jack"), "class Hidden {}");

    Path outputDirectory = tempDir.resolve("out");
    List<Path> outputs = command.execute(tempDir, Optional.of(outputDirectory));

    assertEquals(List.of(outputDirectory.resolve("MainP.xml")), outputs);
    assertFalse(Files.exists(outputDirectory.resolve("HiddenP.xml")));
  }

  @Test
  void ignoresFilesWithoutCaseSensitiveJackExtension() throws Exception {
    write("Main.jack", "class Main {}");
    write("Other.JACK", "class Other {}");
    write("Backup.jack.bak", "class Backup {}");
    write("Notes.txt", "text");

    Path outputDirectory = tempDir.resolve("out");
    List<Path> outputs = command.execute(tempDir, Optional.of(outputDirectory));

    assertEquals(List.of(outputDirectory.resolve("MainP.xml")), outputs);
    assertFalse(Files.exists(outputDirectory.resolve("OtherP.xml")));
    assertFalse(Files.exists(outputDirectory.resolve("BackupP.xml")));
  }

  @Test
  void stopsAtFirstSyntaxErrorAndPreservesEarlierOutput() throws Exception {
    write("A.jack", "class A {}");
    Path invalid = write("B.jack", "class B {");
    write("C.jack", "class C {}");

    Path outputDirectory = tempDir.resolve("out");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(tempDir, Optional.of(outputDirectory)));

    assertEquals(invalid, error.sourceFile());
    assertInstanceOf(SyntaxException.class, error.getCause());
    assertTrue(Files.isRegularFile(outputDirectory.resolve("AP.xml")));
    assertFalse(Files.exists(outputDirectory.resolve("BP.xml")));
    assertFalse(Files.exists(outputDirectory.resolve("CP.xml")));
  }

  @Test
  void stopsAtFirstLexicalErrorAndPreservesEarlierOutput() throws Exception {
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
    assertTrue(Files.isRegularFile(outputDirectory.resolve("AP.xml")));
    assertFalse(Files.exists(outputDirectory.resolve("BP.xml")));
    assertFalse(Files.exists(outputDirectory.resolve("CP.xml")));
  }

  @Test
  void stopsAtFirstOutputCollisionWithoutProcessingLaterSources() throws Exception {
    write("A.jack", "class A {}");
    Path sourceB = write("B.jack", "class B {}");
    write("C.jack", "class C {}");

    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Files.writeString(outputDirectory.resolve("BP.xml"), "existing");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(tempDir, Optional.of(outputDirectory)));

    assertEquals(sourceB, error.sourceFile());
    assertInstanceOf(FileAlreadyExistsException.class, error.getCause());
    assertTrue(Files.isRegularFile(outputDirectory.resolve("AP.xml")));
    assertEquals("existing", Files.readString(outputDirectory.resolve("BP.xml")));
    assertFalse(Files.exists(outputDirectory.resolve("CP.xml")));
  }

  @Test
  void rejectsEmptySourceAtOriginalEof() throws Exception {
    Path source = write("Empty.jack", "");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(tempDir.resolve("out"))));

    SyntaxException syntax = assertInstanceOf(SyntaxException.class, error.getCause());

    assertEquals("KEYWORD 'class'", syntax.expected());
    assertTrue(syntax.found().isEmpty());
    assertEquals(1, syntax.position().line());
    assertEquals(1, syntax.position().column());
    assertFalse(Files.exists(tempDir.resolve("out/EmptyP.xml")));
  }

  @Test
  void rejectsTriviaOnlySourceAtOriginalEof() throws Exception {
    Path source = write("Empty.jack", "// heading\n/* comment */");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(tempDir.resolve("out"))));

    SyntaxException syntax = assertInstanceOf(SyntaxException.class, error.getCause());

    assertEquals("KEYWORD 'class'", syntax.expected());
    assertTrue(syntax.found().isEmpty());
    assertEquals(2, syntax.position().line());
    assertFalse(Files.exists(tempDir.resolve("out/EmptyP.xml")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"class", "class A", "class A {", "class A { function void f() { return; }"})
  void rejectsIncompleteJackClasses(String incomplete) throws Exception {
    Path source = write("Incomplete.jack", incomplete);
    Path outputDirectory = tempDir.resolve("out");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(outputDirectory)));

    assertInstanceOf(SyntaxException.class, error.getCause());
    assertFalse(Files.exists(outputDirectory.resolve("IncompleteP.xml")));
  }

  @Test
  void rejectsTokensAfterClosingClassBrace() throws Exception {
    Path source = write("Main.jack", "class Main {} class Other {}");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(tempDir.resolve("out"))));

    SyntaxException syntax = assertInstanceOf(SyntaxException.class, error.getCause());

    assertEquals("end of input", syntax.expected());
    assertEquals("class", syntax.found().orElseThrow().value());
    assertEquals(source, error.sourceFile());
    assertFalse(Files.exists(tempDir.resolve("out/MainP.xml")));
  }

  @Test
  void preservesSyntaxErrorPositionAndOriginalCause() throws Exception {
    Path source =
        write(
            "Invalid.jack",
            "class Main {\n" + "  function void run() {\n" + "    let x = ;\n" + "  }\n" + "}");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(tempDir.resolve("out"))));

    SyntaxException syntax = assertInstanceOf(SyntaxException.class, error.getCause());

    assertEquals(source, error.sourceFile());
    assertEquals("term", syntax.expected());
    assertEquals(";", syntax.found().orElseThrow().value());
    assertEquals(3, syntax.position().line());
    assertTrue(error.getMessage().contains(source.toString()));
    assertFalse(Files.exists(tempDir.resolve("out/InvalidP.xml")));
  }

  @Test
  void preservesLexicalErrorPositionAndOriginalCause() throws Exception {
    Path source = write("Invalid.jack", "class Main {}\n@");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(tempDir.resolve("out"))));

    LexicalException lexical = assertInstanceOf(LexicalException.class, error.getCause());

    assertEquals(source, error.sourceFile());
    assertEquals(2, lexical.position().line());
    assertEquals(1, lexical.position().column());
    assertFalse(Files.exists(tempDir.resolve("out/InvalidP.xml")));
  }

  @Test
  void rejectsXmlForbiddenCharactersBeforePublishingOutput() throws Exception {
    String invalidSource =
        "class Main {"
            + " function void f() {"
            + " do Output.printString(\"a"
            + ((char) 1)
            + "b\");"
            + " return;"
            + " }"
            + "}";

    Path source = write("Invalid.jack", invalidSource);
    Path outputDirectory = tempDir.resolve("out");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(outputDirectory)));

    IllegalArgumentException cause =
        assertInstanceOf(IllegalArgumentException.class, error.getCause());

    assertTrue(cause.getMessage().contains("Invalid XML character"));
    assertFalse(Files.exists(outputDirectory.resolve("InvalidP.xml")));
    assertEquals(invalidSource, Files.readString(source));
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
    assertFalse(Files.exists(tempDir.resolve("out/MalformedP.xml")));
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
  void rejectsUnsupportedSourceExtension() throws Exception {
    Path source = write("Main.txt", "class Main {}");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(tempDir.resolve("out"))));

    assertEquals(source, error.sourceFile());
    assertInstanceOf(IllegalArgumentException.class, error.getCause());
  }

  @Test
  void rejectsHardLinkAliasToSource() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Path destination = outputDirectory.resolve("MainP.xml");

    Files.createLink(destination, source);

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(outputDirectory)));

    assertEquals(source, error.sourceFile());
    assertInstanceOf(IOException.class, error.getCause());
    assertTrue(error.getCause().getMessage().contains("aliases the source"));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void rejectsSymbolicLinkOutputDestination() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Path protectedFile = Files.writeString(tempDir.resolve("protected.xml"), "protected");

    Files.createSymbolicLink(outputDirectory.resolve("MainP.xml"), protectedFile);

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(outputDirectory)));

    assertEquals(source, error.sourceFile());
    assertInstanceOf(IOException.class, error.getCause());
    assertEquals("protected", Files.readString(protectedFile));
  }

  @Test
  void rejectsSymbolicLinkOutputDirectory() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path realDirectory = Files.createDirectory(tempDir.resolve("real"));
    Path link = tempDir.resolve("alias");

    Files.createSymbolicLink(link, realDirectory);

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(source, Optional.of(link)));

    assertEquals(source, error.sourceFile());
    assertInstanceOf(IOException.class, error.getCause());
    assertFalse(Files.exists(realDirectory.resolve("MainP.xml")));
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

    assertEquals(source, error.sourceFile());
    assertInstanceOf(IOException.class, error.getCause());
    assertFalse(Files.exists(outputDirectory.resolve("MainP.xml")));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void rejectsOutputDirectoryAtGeneratedFilePath() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Files.createDirectory(outputDirectory.resolve("MainP.xml"));

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(outputDirectory)));

    assertInstanceOf(FileAlreadyExistsException.class, error.getCause());
    assertTrue(Files.isDirectory(outputDirectory.resolve("MainP.xml")));
  }

  @Test
  void doesNotLeaveTemporaryFilesAfterOutputCollision() throws Exception {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Files.writeString(outputDirectory.resolve("MainP.xml"), "existing");

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
  void doesNotPublishOrLeaveTemporaryFilesAfterSyntaxFailure() throws Exception {
    Path source = write("Main.jack", "class Main {");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));

    assertThrows(
        SourceProcessingException.class,
        () -> command.execute(source, Optional.of(outputDirectory)));

    try (var entries = Files.list(outputDirectory)) {
      assertEquals(0L, entries.count());
    }
  }

  @Test
  void supportsRepeatedExecutionsWithDifferentInputs() throws Exception {
    Path first = write("First.jack", "class First {}");
    Path second = write("Second.jack", "class Second {}");

    List<Path> firstOutputs = command.execute(first, Optional.of(tempDir.resolve("first-out")));
    List<Path> secondOutputs = command.execute(second, Optional.of(tempDir.resolve("second-out")));

    assertEquals(List.of(tempDir.resolve("first-out/FirstP.xml")), firstOutputs);
    assertEquals(List.of(tempDir.resolve("second-out/SecondP.xml")), secondOutputs);
    assertTrue(Files.readString(firstOutputs.get(0)).contains("<identifier> First </identifier>"));
    assertTrue(
        Files.readString(secondOutputs.get(0)).contains("<identifier> Second </identifier>"));
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
