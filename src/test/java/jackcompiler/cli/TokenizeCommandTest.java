package jackcompiler.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.LexicalException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TokenizeCommandTest {

  @TempDir Path tempDir;

  private final TokenizeCommand command = new TokenizeCommand();

  @Test
  void tokenizesSingleJackFileIntoExpectedXml() throws IOException {
    Path source = write("Main.jack", "class Main {}");

    List<Path> outputs = command.execute(source);

    assertEquals(List.of(tempDir.resolve("MainT.xml")), outputs);

    assertEquals(
        "<tokens>\n"
            + "<keyword> class </keyword>\n"
            + "<identifier> Main </identifier>\n"
            + "<symbol> { </symbol>\n"
            + "<symbol> } </symbol>\n"
            + "</tokens>\n",
        Files.readString(outputs.get(0)));
  }

  @Test
  void returnsAbsoluteNormalizedOutputForRelativeSource() throws IOException {
    Path source = write("Main.jack", "class Main {}");
    Path relative = Path.of("").toAbsolutePath().relativize(source);

    List<Path> outputs = command.execute(relative);

    assertEquals(List.of(tempDir.resolve("MainT.xml")), outputs);
    assertTrue(outputs.get(0).isAbsolute());
  }

  @Test
  void processesDirectoryInAlphabeticalOrder() throws IOException {
    write("Zebra.jack", "class Zebra {}");
    write("Alpha.jack", "class Alpha {}");
    write("Main.jack", "class Main {}");

    List<Path> outputs = command.execute(tempDir);

    assertEquals(
        List.of(
            tempDir.resolve("AlphaT.xml"),
            tempDir.resolve("MainT.xml"),
            tempDir.resolve("ZebraT.xml")),
        outputs);

    assertTrue(Files.readString(outputs.get(0)).contains("Alpha"));
    assertTrue(Files.readString(outputs.get(1)).contains("Main"));
    assertTrue(Files.readString(outputs.get(2)).contains("Zebra"));
  }

  @Test
  void ignoresNonJackFilesInDirectory() throws IOException {
    write("Main.jack", "class Main {}");
    write("notes.txt", "notes");
    write("Other.JACK", "class Other {}");
    write("Backup.jack.bak", "backup");

    List<Path> outputs = command.execute(tempDir);

    assertEquals(List.of(tempDir.resolve("MainT.xml")), outputs);
    assertFalse(Files.exists(tempDir.resolve("OtherT.xml")));
    assertFalse(Files.exists(tempDir.resolve("BackupT.xml")));
  }

  @Test
  void doesNotRecurseIntoSubdirectories() throws IOException {
    write("Main.jack", "class Main {}");

    Path nested = Files.createDirectory(tempDir.resolve("nested"));
    Files.writeString(nested.resolve("Other.jack"), "class Other {}");

    List<Path> outputs = command.execute(tempDir);

    assertEquals(List.of(tempDir.resolve("MainT.xml")), outputs);
    assertFalse(Files.exists(nested.resolve("OtherT.xml")));
  }

  @Test
  void createsXmlBesideSourceInExistingNestedDirectory() throws IOException {
    Path nested = Files.createDirectory(tempDir.resolve("project"));
    Path source =
        Files.writeString(nested.resolve("Main.jack"), "class Main {}", StandardCharsets.UTF_8);

    List<Path> outputs = command.execute(source);

    assertEquals(List.of(nested.resolve("MainT.xml")), outputs);
    assertTrue(Files.isRegularFile(outputs.get(0)));
  }

  @Test
  void preservesUtf8UnicodeFromSource() throws IOException {
    Path source = write("Olá Mundo.jack", "do Output.printString(\"Olá 😀\");");

    List<Path> outputs = command.execute(source);
    String xml = Files.readString(outputs.get(0), StandardCharsets.UTF_8);

    assertEquals(List.of(tempDir.resolve("Olá MundoT.xml")), outputs);
    assertTrue(xml.contains("<stringConstant> Olá 😀 </stringConstant>"));
  }

  @Test
  void escapesXmlSpecialCharacters() throws IOException {
    Path source = write("Main.jack", "let text = \"<>&'\";");

    String xml = Files.readString(command.execute(source).get(0));

    assertTrue(xml.contains("<stringConstant> &lt;&gt;&amp;&apos; </stringConstant>"));
  }

  @Test
  void excludesCommentsAndWhitespaceFromXml() throws IOException {
    Path source = write("Main.jack", "// heading\n/* docs */class Main {} // tail");

    String xml = Files.readString(command.execute(source).get(0));

    assertTrue(xml.contains("<keyword> class </keyword>"));
    assertTrue(xml.contains("<identifier> Main </identifier>"));
    assertFalse(xml.contains("heading"));
    assertFalse(xml.contains("docs"));
    assertFalse(xml.contains("tail"));
  }

  @Test
  void preservesCommentMarkersInsideStrings() throws IOException {
    Path source = write("Main.jack", "let text = \"// and /* are text\";");

    String xml = Files.readString(command.execute(source).get(0));

    assertTrue(xml.contains("<stringConstant> // and /* are text </stringConstant>"));
  }

  @Test
  void preservesOriginalJackFileBytes() throws IOException {
    Path source = write("Main.jack", "class Main {\r\n}\r\n");
    byte[] original = Files.readAllBytes(source);

    command.execute(source);

    assertArrayEquals(original, Files.readAllBytes(source));
  }

  @Test
  void overwritesPreviouslyGeneratedTokenXml() throws IOException {
    Path source = write("Main.jack", "class Main {}");
    Path output = tempDir.resolve("MainT.xml");

    Files.writeString(output, "obsolete content");

    command.execute(source);

    String xml = Files.readString(output);

    assertFalse(xml.contains("obsolete content"));
    assertTrue(xml.startsWith("<tokens>\n"));
    assertTrue(xml.endsWith("</tokens>\n"));
  }

  @Test
  void createsEmptyTokenDocumentForEmptySource() throws IOException {
    Path source = write("Empty.jack", "");

    String xml = Files.readString(command.execute(source).get(0));

    assertEquals("<tokens>\n</tokens>\n", xml);
  }

  @Test
  void createsEmptyTokenDocumentForTriviaOnlySource() throws IOException {
    Path source = write("Comments.jack", " \r\n// hi\n/* block */");

    String xml = Files.readString(command.execute(source).get(0));

    assertEquals("<tokens>\n</tokens>\n", xml);
  }

  @Test
  void returnsUnmodifiableOutputPaths() throws IOException {
    Path source = write("Main.jack", "class Main {}");

    List<Path> outputs = command.execute(source);

    assertThrows(
        UnsupportedOperationException.class, () -> outputs.add(tempDir.resolve("OtherT.xml")));
  }

  @Test
  void rejectsMissingSourceFile() {
    Path missing = tempDir.resolve("Missing.jack");

    assertThrows(NoSuchFileException.class, () -> command.execute(missing));
  }

  @Test
  void rejectsMissingDirectory() {
    Path missing = tempDir.resolve("missing");

    assertThrows(NoSuchFileException.class, () -> command.execute(missing));
  }

  @Test
  void rejectsNonJackSingleFile() throws IOException {
    Path source = write("Main.txt", "class Main {}");

    assertThrows(IllegalArgumentException.class, () -> command.execute(source));

    assertFalse(Files.exists(tempDir.resolve("MainT.xml")));
  }

  @Test
  void rejectsEmptyDirectory() {
    IOException error = assertThrows(IOException.class, () -> command.execute(tempDir));

    assertTrue(error.getMessage().contains("No .jack files"));
  }

  @Test
  void rejectsDirectoryWithoutJackSources() throws IOException {
    write("README.md", "documentation");

    IOException error = assertThrows(IOException.class, () -> command.execute(tempDir));

    assertTrue(error.getMessage().contains("No .jack files"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"@", "let x = 32768;", "let x = \"unfinished", "/* unfinished"})
  void invalidSourceDoesNotCreateItsXmlOutput(String invalid) throws IOException {
    Path source = write("Invalid.jack", invalid);

    LexicalException error = assertThrows(LexicalException.class, () -> command.execute(source));

    assertNotNull(error.position());
    assertFalse(Files.exists(tempDir.resolve("InvalidT.xml")));
    assertEquals(invalid, Files.readString(source));
  }

  @Test
  void stopsDirectoryProcessingOnFirstLexicalError() throws IOException {
    write("A.jack", "class A {}");
    write("B.jack", "let x = 32768;");
    write("C.jack", "class C {}");

    assertThrows(LexicalException.class, () -> command.execute(tempDir));

    assertTrue(Files.exists(tempDir.resolve("AT.xml")));
    assertFalse(Files.exists(tempDir.resolve("BT.xml")));
    assertFalse(Files.exists(tempDir.resolve("CT.xml")));
  }

  @Test
  void propagatesInvalidXmlCharacterError() throws IOException {
    String sourceText = "do Output.printString(\"a" + (char) 1 + "b\");";
    Path source = write("Main.jack", sourceText);

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> command.execute(source));

    assertTrue(error.getMessage().contains("Invalid XML character"));
    assertFalse(Files.exists(tempDir.resolve("MainT.xml")));
  }

  @Test
  void propagatesXmlOutputWriteFailure() throws IOException {
    Path source = write("Main.jack", "class Main {}");
    Files.createDirectory(tempDir.resolve("MainT.xml"));

    assertThrows(IOException.class, () -> command.execute(source));
  }

  @Test
  void integratesWithParsedCommandLineArguments() throws IOException {
    Path source = write("Main.jack", "class Main {}");

    CommandLineArguments arguments =
        CommandLineArguments.parse(new String[] {"tokenize", source.toString()});

    List<Path> outputs = command.execute(arguments.input());

    assertEquals(CommandLineArguments.Mode.TOKENIZE, arguments.mode());
    assertEquals(List.of(tempDir.resolve("MainT.xml")), outputs);
  }

  @Test
  void rejectsNullInputPath() {
    assertThrows(NullPointerException.class, () -> command.execute(null));
  }

  @Test
  void supportsRepeatedExecution() throws IOException {
    Path source = write("Main.jack", "class Main {}");

    List<Path> firstOutputs = command.execute(source);
    String firstXml = Files.readString(firstOutputs.get(0));

    List<Path> secondOutputs = command.execute(source);
    String secondXml = Files.readString(secondOutputs.get(0));

    assertEquals(firstOutputs, secondOutputs);
    assertEquals(firstXml, secondXml);
  }

  private Path write(String name, String content) throws IOException {
    return Files.writeString(tempDir.resolve(name), content, StandardCharsets.UTF_8);
  }
}
