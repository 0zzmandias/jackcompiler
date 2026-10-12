package jackcompiler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MainTest {

  @TempDir Path tempDir;

  private final ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
  private final ByteArrayOutputStream standardError = new ByteArrayOutputStream();

  @Test
  void tokenizesSingleFileAndReportsOutputPath() throws IOException {
    Path source = write("Main.jack", "class Main {}");

    int status = invoke("tokenize", source.toString());

    assertEquals(0, status);
    assertEquals("Generated: " + tempDir.resolve("MainT.xml") + "\n", stdout());
    assertEquals("", stderr());

    assertEquals(
        "<tokens>\n"
            + "<keyword> class </keyword>\n"
            + "<identifier> Main </identifier>\n"
            + "<symbol> { </symbol>\n"
            + "<symbol> } </symbol>\n"
            + "</tokens>\n",
        Files.readString(tempDir.resolve("MainT.xml")));
  }

  @Test
  void tokenizesDirectoryAndReportsOutputsInAlphabeticalOrder() throws IOException {
    write("Zebra.jack", "class Zebra {}");
    write("Alpha.jack", "class Alpha {}");

    int status = invoke("tokenize", tempDir.toString());

    assertEquals(0, status);
    assertEquals(
        "Generated: "
            + tempDir.resolve("AlphaT.xml")
            + "\n"
            + "Generated: "
            + tempDir.resolve("ZebraT.xml")
            + "\n",
        stdout());
    assertEquals("", stderr());

    assertTrue(Files.exists(tempDir.resolve("AlphaT.xml")));
    assertTrue(Files.exists(tempDir.resolve("ZebraT.xml")));
  }

  @Test
  void preservesUnicodeAndEscapesXmlCharacters() throws IOException {
    Path source = write("Olá.jack", "do Output.printString(\"Olá 😀 <&>\");");

    assertEquals(0, invoke("tokenize", source.toString()));

    String xml = Files.readString(tempDir.resolve("OláT.xml"));

    assertTrue(xml.contains("<stringConstant> Olá 😀 &lt;&amp;&gt; </stringConstant>"));
    assertEquals("", stderr());
  }

  @Test
  void preservesOriginalJackSourceBytes() throws IOException {
    Path source = write("Main.jack", "class Main {\r\n}\r\n");
    byte[] original = Files.readAllBytes(source);

    assertEquals(0, invoke("tokenize", source.toString()));

    assertArrayEquals(original, Files.readAllBytes(source));
  }

  @Test
  void overwritesPreviouslyGeneratedXml() throws IOException {
    Path source = write("Main.jack", "class Main {}");
    Path output = tempDir.resolve("MainT.xml");

    Files.writeString(output, "old content");

    assertEquals(0, invoke("tokenize", source.toString()));

    assertFalse(Files.readString(output).contains("old content"));
    assertTrue(Files.readString(output).startsWith("<tokens>\n"));
  }

  @Test
  void handlesEmptyJackSource() throws IOException {
    Path source = write("Empty.jack", "");

    assertEquals(0, invoke("tokenize", source.toString()));

    assertEquals("<tokens>\n</tokens>\n", Files.readString(tempDir.resolve("EmptyT.xml")));
  }

  @Test
  void rejectsMissingCommandLineArguments() {
    int status = invoke();

    assertEquals(2, status);
    assertTrue(stderr().contains("Usage:"));
    assertEquals("", stdout());
  }

  @Test
  void rejectsMissingInputPathArgument() {
    int status = invoke("tokenize");

    assertEquals(2, status);
    assertTrue(stderr().contains("Usage:"));
    assertEquals("", stdout());
  }

  @Test
  void rejectsExtraCommandLineArguments() {
    int status = invoke("tokenize", "Main.jack", "extra");

    assertEquals(2, status);
    assertTrue(stderr().contains("Usage:"));
    assertEquals("", stdout());
  }

  @ParameterizedTest
  @ValueSource(strings = {"compile", "tokens", "Tokenize", "--help"})
  void rejectsUnknownCommands(String command) {
    int status = invoke(command, "Main.jack");

    assertEquals(2, status);
    assertTrue(stderr().contains("Unknown command"));
    assertTrue(stderr().contains("Usage:"));
    assertEquals("", stdout());
  }

  @Test
  void reportsParseCommandAsNotImplemented() throws IOException {
    Path source = write("Main.jack", "class Main {}");

    int status = invoke("parse", source.toString());

    assertEquals(2, status);
    assertTrue(stderr().contains("parse is not implemented yet"));
    assertEquals("", stdout());
    assertFalse(Files.exists(tempDir.resolve("MainT.xml")));
  }

  @Test
  void rejectsMissingSourceFile() {
    Path source = tempDir.resolve("Missing.jack");

    int status = invoke("tokenize", source.toString());

    assertEquals(1, status);
    assertTrue(stderr().contains("I/O error"));
    assertTrue(stderr().contains("Missing.jack"));
    assertEquals("", stdout());
  }

  @Test
  void rejectsUnsupportedSourceExtension() throws IOException {
    Path source = write("Main.txt", "class Main {}");

    int status = invoke("tokenize", source.toString());

    assertEquals(1, status);
    assertTrue(stderr().contains(".jack"));
    assertEquals("", stdout());
    assertFalse(Files.exists(tempDir.resolve("MainT.xml")));
  }

  @Test
  void rejectsDirectoryWithoutJackFiles() {
    int status = invoke("tokenize", tempDir.toString());

    assertEquals(1, status);
    assertTrue(stderr().contains("No .jack files"));
    assertEquals("", stdout());
  }

  @Test
  void reportsLexicalErrorWithLineAndColumn() throws IOException {
    Path source = write("Invalid.jack", "class Main {}\n@");

    int status = invoke("tokenize", source.toString());

    assertEquals(1, status);
    assertTrue(stderr().contains("Lexical error"));
    assertTrue(stderr().contains("line 2, column 1"));
    assertTrue(stderr().contains("Unexpected character"));
    assertEquals("", stdout());
    assertFalse(Files.exists(tempDir.resolve("InvalidT.xml")));
  }

  @Test
  void reportsUnterminatedString() throws IOException {
    Path source = write("Invalid.jack", "let x = \"broken");

    int status = invoke("tokenize", source.toString());

    assertEquals(1, status);
    assertTrue(stderr().contains("line 1, column 9"));
    assertTrue(stderr().contains("Unterminated"));
    assertFalse(Files.exists(tempDir.resolve("InvalidT.xml")));
  }

  @Test
  void reportsOutOfRangeInteger() throws IOException {
    Path source = write("Invalid.jack", "let x = 32768;");

    int status = invoke("tokenize", source.toString());

    assertEquals(1, status);
    assertTrue(stderr().contains("32767"));
    assertTrue(stderr().contains("line 1, column 9"));
    assertFalse(Files.exists(tempDir.resolve("InvalidT.xml")));
  }

  @Test
  void reportsInvalidXmlCharacterAsProcessingError() throws IOException {
    String content = "let s = \"a" + (char) 1 + "b\";";
    Path source = write("Invalid.jack", content);

    int status = invoke("tokenize", source.toString());

    assertEquals(1, status);
    assertTrue(stderr().contains("Invalid XML character"));
    assertFalse(Files.exists(tempDir.resolve("InvalidT.xml")));
  }

  @Test
  void reportsXmlOutputWriteFailure() throws IOException {
    Path source = write("Main.jack", "class Main {}");
    Files.createDirectory(tempDir.resolve("MainT.xml"));

    int status = invoke("tokenize", source.toString());

    assertEquals(1, status);
    assertTrue(stderr().contains("I/O error"));
    assertEquals("", stdout());
  }

  @Test
  void stopsDirectoryProcessingOnFirstInvalidSource() throws IOException {
    write("A.jack", "class A {}");
    write("B.jack", "@");
    write("C.jack", "class C {}");

    int status = invoke("tokenize", tempDir.toString());

    assertEquals(1, status);
    assertTrue(Files.exists(tempDir.resolve("AT.xml")));
    assertFalse(Files.exists(tempDir.resolve("BT.xml")));
    assertFalse(Files.exists(tempDir.resolve("CT.xml")));
    assertEquals("", stdout());
    assertTrue(stderr().contains("Lexical error"));
  }

  @Test
  void rejectsInvalidFileSystemPathAsCommandError() {
    int status = invoke("tokenize", "bad\0path");

    assertEquals(2, status);
    assertTrue(stderr().contains("Invalid input path"));
    assertEquals("", stdout());
  }

  @Test
  void supportsRepeatedExecution() throws IOException {
    Path source = write("Main.jack", "class Main {}");

    assertEquals(0, invoke("tokenize", source.toString()));
    String firstXml = Files.readString(tempDir.resolve("MainT.xml"));

    assertEquals(0, invoke("tokenize", source.toString()));
    String secondXml = Files.readString(tempDir.resolve("MainT.xml"));

    assertEquals(firstXml, secondXml);
    assertEquals("", stderr());
  }

  @Test
  void rejectsNullOutputStreams() {
    String[] args = {"tokenize", "Main.jack"};

    assertThrows(
        NullPointerException.class, () -> Main.run(args, null, new PrintStream(standardError)));

    assertThrows(
        NullPointerException.class, () -> Main.run(args, new PrintStream(standardOutput), null));
  }

  private int invoke(String... arguments) {
    try (PrintStream out = new PrintStream(standardOutput, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(standardError, true, StandardCharsets.UTF_8)) {

      return Main.run(arguments, out, err);
    }
  }

  private String stdout() {
    return standardOutput.toString(StandardCharsets.UTF_8);
  }

  private String stderr() {
    return standardError.toString(StandardCharsets.UTF_8);
  }

  private Path write(String name, String source) throws IOException {
    return Files.writeString(tempDir.resolve(name), source, StandardCharsets.UTF_8);
  }
}
