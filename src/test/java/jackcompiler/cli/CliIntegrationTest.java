package jackcompiler.cli;

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

class CliIntegrationTest {

  @TempDir Path tempDir;

  @Test
  void tokenizesSingleFileIntoCustomDirectory() throws IOException {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = tempDir.resolve("out");

    Execution result =
        execute("tokenize", source.toString(), "--output-dir", outputDirectory.toString());

    Path output = outputDirectory.resolve("MainT.xml");

    assertEquals(0, result.status());
    assertEquals("Generated: " + output + "\n", result.stdout());
    assertEquals("", result.stderr());
    assertEquals(
        "<tokens>\n"
            + "<keyword> class </keyword>\n"
            + "<identifier> Main </identifier>\n"
            + "<symbol> { </symbol>\n"
            + "<symbol> } </symbol>\n"
            + "</tokens>\n",
        Files.readString(output));
  }

  @Test
  void usesDefaultGeneratedTokensDirectory() throws IOException {
    String stem = "Cli_" + tempDir.getFileName();
    Path source = write(stem + ".jack", "class Main {}");
    Path expected =
        Path.of("target", "generated-tokens", stem + "T.xml").toAbsolutePath().normalize();

    try {
      Execution result = execute("tokenize", source.toString());

      assertEquals(0, result.status());
      assertEquals("Generated: " + expected + "\n", result.stdout());
      assertEquals("", result.stderr());
      assertTrue(Files.isRegularFile(expected));
    } finally {
      Files.deleteIfExists(expected);
    }
  }

  @Test
  void processesDirectoryInAlphabeticalOrder() throws IOException {
    write("Zebra.jack", "class Zebra {}");
    write("Alpha.jack", "class Alpha {}");

    Path outputDirectory = tempDir.resolve("out");

    Execution result =
        execute("tokenize", tempDir.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(0, result.status());
    assertEquals(
        "Generated: "
            + outputDirectory.resolve("AlphaT.xml")
            + "\n"
            + "Generated: "
            + outputDirectory.resolve("ZebraT.xml")
            + "\n",
        result.stdout());

    assertEquals("", result.stderr());
    assertTrue(Files.exists(outputDirectory.resolve("AlphaT.xml")));
    assertTrue(Files.exists(outputDirectory.resolve("ZebraT.xml")));
  }

  @Test
  void preservesUnicodeAndEscapesXmlCharacters() throws IOException {
    Path source = write("Olá.jack", "do Output.printString(\"Olá 😀 <&>\");");
    Path outputDirectory = tempDir.resolve("out");

    Execution result =
        execute("tokenize", source.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(0, result.status());
    assertTrue(
        Files.readString(outputDirectory.resolve("OláT.xml"))
            .contains("<stringConstant> Olá 😀 &lt;&amp;&gt; </stringConstant>"));
    assertEquals("", result.stderr());
  }

  @Test
  void preservesOriginalSourceBytes() throws IOException {
    Path source = write("Main.jack", "class Main {\r\n}\r\n");
    byte[] before = Files.readAllBytes(source);

    Execution result =
        execute("tokenize", source.toString(), "--output-dir", tempDir.resolve("out").toString());

    assertEquals(0, result.status());
    assertArrayEquals(before, Files.readAllBytes(source));
  }

  @Test
  void rejectsExistingOutputInCustomDirectory() throws IOException {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Path output = Files.writeString(outputDirectory.resolve("MainT.xml"), "original");

    Execution result =
        execute("tokenize", source.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(1, result.status());
    assertTrue(result.stderr().contains("I/O error"));
    assertTrue(result.stderr().contains("Main.jack"));
    assertEquals("", result.stdout());
    assertEquals("original", Files.readString(output));
  }

  @Test
  void handlesEmptySource() throws IOException {
    Path source = write("Empty.jack", "");
    Path outputDirectory = tempDir.resolve("out");

    Execution result =
        execute("tokenize", source.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(0, result.status());
    assertEquals("<tokens>\n</tokens>\n", Files.readString(outputDirectory.resolve("EmptyT.xml")));
  }

  @Test
  void helpSucceedsAndWritesUsageToStdout() {
    Execution result = execute("--help");

    assertEquals(0, result.status());
    assertTrue(result.stdout().contains("tokenize"));
    assertTrue(result.stdout().contains("parse"));
    assertTrue(result.stdout().contains("--output-dir"));
    assertEquals("", result.stderr());
  }

  @Test
  void rejectsMissingCommand() {
    Execution result = execute();

    assertEquals(2, result.status());
    assertTrue(result.stderr().contains("Usage:"));
    assertEquals("", result.stdout());
  }

  @Test
  void rejectsMissingInput() {
    Execution result = execute("tokenize");

    assertEquals(2, result.status());
    assertTrue(result.stderr().contains("input path"));
    assertEquals("", result.stdout());
  }

  @Test
  void rejectsExtraPositionalArguments() {
    Execution result = execute("tokenize", "Main.jack", "extra");

    assertEquals(2, result.status());
    assertTrue(result.stderr().contains("Unexpected argument"));
    assertEquals("", result.stdout());
  }

  @ParameterizedTest
  @ValueSource(strings = {"compile", "tokens", "Tokenize", "help"})
  void rejectsUnavailableCommands(String command) {
    Execution result = execute(command, "Main.jack");

    assertEquals(2, result.status());
    assertTrue(result.stderr().contains("Unknown command"));
    assertEquals("", result.stdout());
  }

  @Test
  void rejectsHelpWithAdditionalArguments() {
    Execution result = execute("--help", "Main.jack");

    assertEquals(2, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Usage:"));
  }

  @Test
  void rejectsUnknownOption() {
    Execution result = execute("tokenize", "Main.jack", "--unknown");

    assertEquals(2, result.status());
    assertTrue(result.stderr().contains("Unknown option"));
    assertEquals("", result.stdout());
  }

  @Test
  void rejectsRepeatedOutputDirectoryOption() {
    Execution result = execute("tokenize", "Main.jack", "--output-dir", "a", "--output-dir", "b");

    assertEquals(2, result.status());
    assertTrue(result.stderr().contains("Duplicate"));
  }

  @Test
  void rejectsMissingOutputDirectoryValue() {
    Execution result = execute("tokenize", "Main.jack", "--output-dir");

    assertEquals(2, result.status());
    assertTrue(result.stderr().contains("Missing directory"));
  }

  @Test
  void rejectsInvalidInputPathAsArgumentError() {
    Execution result = execute("tokenize", "bad\0path");

    assertEquals(2, result.status());
    assertTrue(result.stderr().contains("Invalid input path"));
    assertEquals("", result.stdout());
  }

  @Test
  void rejectsInvalidOutputPathAsArgumentError() {
    Execution result = execute("tokenize", "Main.jack", "--output-dir", "bad\0path");

    assertEquals(2, result.status());
    assertTrue(result.stderr().contains("Invalid output directory path"));
  }

  @Test
  void reportsMissingSourceFileWithPath() {
    Path missing = tempDir.resolve("Missing.jack");

    Execution result =
        execute("tokenize", missing.toString(), "--output-dir", tempDir.resolve("out").toString());

    assertEquals(1, result.status());
    assertTrue(result.stderr().contains("I/O error"));
    assertTrue(result.stderr().contains("Missing.jack"));
    assertEquals("", result.stdout());
  }

  @Test
  void rejectsUnsupportedSourceExtension() throws IOException {
    Path source = write("Main.txt", "class Main {}");

    Execution result =
        execute("tokenize", source.toString(), "--output-dir", tempDir.resolve("out").toString());

    assertEquals(1, result.status());
    assertTrue(result.stderr().contains(".jack"));
    assertTrue(result.stderr().contains("Main.txt"));
    assertEquals("", result.stdout());
  }

  @Test
  void rejectsDirectoryWithoutJackFiles() {
    Execution result =
        execute("tokenize", tempDir.toString(), "--output-dir", tempDir.resolve("out").toString());

    assertEquals(1, result.status());
    assertTrue(result.stderr().contains("No .jack files"));
    assertEquals("", result.stdout());
  }

  @Test
  void reportsLexicalErrorWithSourceLineAndColumn() throws IOException {
    Path source = write("Invalid.jack", "class Main {}\n@");
    Path outputDirectory = tempDir.resolve("out");

    Execution result =
        execute("tokenize", source.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(1, result.status());
    assertTrue(result.stderr().contains("Lexical error"));
    assertTrue(result.stderr().contains(source.toString()));
    assertTrue(result.stderr().contains("line 2, column 1"));
    assertTrue(result.stderr().contains("Unexpected character"));
    assertEquals("", result.stdout());
    assertFalse(Files.exists(outputDirectory.resolve("InvalidT.xml")));
  }

  @Test
  void reportsUnterminatedString() throws IOException {
    Path source = write("Invalid.jack", "let x = \"broken");

    Execution result =
        execute("tokenize", source.toString(), "--output-dir", tempDir.resolve("out").toString());

    assertEquals(1, result.status());
    assertTrue(result.stderr().contains("line 1, column 9"));
    assertTrue(result.stderr().contains("Unterminated"));
  }

  @Test
  void reportsOutOfRangeInteger() throws IOException {
    Path source = write("Invalid.jack", "let x = 32768;");

    Execution result =
        execute("tokenize", source.toString(), "--output-dir", tempDir.resolve("out").toString());

    assertEquals(1, result.status());
    assertTrue(result.stderr().contains("32767"));
    assertTrue(result.stderr().contains("line 1, column 9"));
  }

  @Test
  void reportsSerializationFailureWithoutOutput() throws IOException {
    Path source = write("Invalid.jack", "let s = \"a\u0001b\";");
    Path outputDirectory = tempDir.resolve("out");

    Execution result =
        execute("tokenize", source.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(1, result.status());
    assertTrue(result.stderr().contains("Processing error"));
    assertTrue(result.stderr().contains(source.toString()));
    assertTrue(result.stderr().contains("Invalid XML character"));
    assertEquals("", result.stdout());
    assertFalse(Files.exists(outputDirectory.resolve("InvalidT.xml")));
  }

  @Test
  void reportsOutputWriteFailure() throws IOException {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Files.createDirectory(outputDirectory.resolve("MainT.xml"));

    Execution result =
        execute("tokenize", source.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(1, result.status());
    assertTrue(result.stderr().contains("I/O error"));
    assertEquals("", result.stdout());
  }

  @Test
  void stopsAfterFirstInvalidSource() throws IOException {
    write("A.jack", "class A {}");
    write("B.jack", "@");
    write("C.jack", "class C {}");

    Path outputDirectory = tempDir.resolve("out");

    Execution result =
        execute("tokenize", tempDir.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(1, result.status());
    assertTrue(Files.exists(outputDirectory.resolve("AT.xml")));
    assertFalse(Files.exists(outputDirectory.resolve("BT.xml")));
    assertFalse(Files.exists(outputDirectory.resolve("CT.xml")));
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Lexical error"));
  }

  @Test
  void rejectsMalformedUtf8WithoutInventingSourcePosition() throws IOException {
    Path source = tempDir.resolve("Malformed.jack");
    Files.write(source, new byte[] {(byte) 0xC3, (byte) 0x28});

    Execution result =
        execute("tokenize", source.toString(), "--output-dir", tempDir.resolve("out").toString());

    assertEquals(1, result.status());
    assertTrue(result.stderr().contains("I/O error"));
    assertTrue(result.stderr().contains("Malformed.jack"));
    assertFalse(result.stderr().contains("line 1, column"));
  }

  @Test
  void rejectsNullOutputStreams() {
    String[] args = {"tokenize", "Main.jack"};

    assertThrows(
        NullPointerException.class,
        () -> JackCompiler.run(args, null, new PrintStream(new ByteArrayOutputStream())));

    assertThrows(
        NullPointerException.class,
        () -> JackCompiler.run(args, new PrintStream(new ByteArrayOutputStream()), null));
  }

  @Test
  void parsesSingleFileIntoCustomDirectory() throws IOException {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = tempDir.resolve("syntax");

    Execution result =
        execute("parse", source.toString(), "--output-dir", outputDirectory.toString());

    Path output = outputDirectory.resolve("MainP.xml");

    assertEquals(0, result.status());
    assertEquals("", result.stderr());
    assertEquals("Generated: " + output + "\n", result.stdout());
    assertEquals(
        "<class>\n"
            + "  <keyword> class </keyword>\n"
            + "  <identifier> Main </identifier>\n"
            + "  <symbol> { </symbol>\n"
            + "  <symbol> } </symbol>\n"
            + "</class>\n",
        Files.readString(output, StandardCharsets.UTF_8));
  }

  @Test
  void parsesComplexClassWithStatementsAndEscapedStrings() throws IOException {
    Path source =
        write(
            "Main.jack",
            "class Main {"
                + " function void main() {"
                + " do Output.printString(\"Olá 😀 <&>\");"
                + " return;"
                + " }"
                + "}");
    Path outputDirectory = tempDir.resolve("syntax");

    Execution result =
        execute("parse", source.toString(), "--output-dir", outputDirectory.toString());

    String xml = Files.readString(outputDirectory.resolve("MainP.xml"));

    assertEquals(0, result.status());
    assertEquals("", result.stderr());
    assertTrue(xml.startsWith("<class>\n"));
    assertTrue(xml.contains("<subroutineDec>"));
    assertTrue(xml.contains("<doStatement>"));
    assertTrue(xml.contains("<returnStatement>"));
    assertTrue(xml.contains("<stringConstant> Olá 😀 &lt;&amp;&gt; </stringConstant>"));
    assertTrue(xml.endsWith("</class>\n"));
    assertFalse(xml.contains("<tokens>"));
  }

  @Test
  void parseUsesDefaultGeneratedParserDirectory() throws IOException {
    String stem = "ParserCli_" + tempDir.getFileName();
    Path source = write(stem + ".jack", "class Main {}");
    Path expected =
        Path.of("target", "generated-parser", stem + "P.xml").toAbsolutePath().normalize();

    try {
      Execution result = execute("parse", source.toString());

      assertEquals(0, result.status());
      assertEquals("Generated: " + expected + "\n", result.stdout());
      assertEquals("", result.stderr());
      assertTrue(Files.isRegularFile(expected));
    } finally {
      Files.deleteIfExists(expected);
    }
  }

  @Test
  void parseProcessesDirectoryInAlphabeticalOrder() throws IOException {
    write("Zebra.jack", "class Zebra {}");
    write("Alpha.jack", "class Alpha {}");

    Path outputDirectory = tempDir.resolve("syntax");

    Execution result =
        execute("parse", tempDir.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(0, result.status());
    assertEquals("", result.stderr());
    assertEquals(
        "Generated: "
            + outputDirectory.resolve("AlphaP.xml")
            + "\n"
            + "Generated: "
            + outputDirectory.resolve("ZebraP.xml")
            + "\n",
        result.stdout());
    assertTrue(Files.isRegularFile(outputDirectory.resolve("AlphaP.xml")));
    assertTrue(Files.isRegularFile(outputDirectory.resolve("ZebraP.xml")));
  }

  @Test
  void tokenizationAndParsingCanShareCustomOutputDirectory() throws IOException {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = tempDir.resolve("generated");

    Execution tokenization =
        execute("tokenize", source.toString(), "--output-dir", outputDirectory.toString());
    Execution parsing =
        execute("parse", source.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(0, tokenization.status());
    assertEquals(0, parsing.status());
    assertEquals("", tokenization.stderr());
    assertEquals("", parsing.stderr());
    assertTrue(Files.readString(outputDirectory.resolve("MainT.xml")).startsWith("<tokens>\n"));
    assertTrue(Files.readString(outputDirectory.resolve("MainP.xml")).startsWith("<class>\n"));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void parsePreservesOriginalSourceBytes() throws IOException {
    Path source = write("Main.jack", "class Main {\r\n}\r\n");
    byte[] before = Files.readAllBytes(source);

    Execution result =
        execute("parse", source.toString(), "--output-dir", tempDir.resolve("out").toString());

    assertEquals(0, result.status());
    assertArrayEquals(before, Files.readAllBytes(source));
  }

  @Test
  void parseReportsSyntaxErrorWithOriginalLineAndColumn() throws IOException {
    Path source =
        write(
            "Broken.jack",
            "class Main {\n" + "  function void main() {\n" + "    let x = ;\n" + "  }\n" + "}");
    Path outputDirectory = tempDir.resolve("out");

    Execution result =
        execute("parse", source.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(1, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Syntax error"));
    assertTrue(result.stderr().contains(source.toString()));
    assertTrue(result.stderr().contains("line 3, column 13"));
    assertTrue(result.stderr().contains("Expected term"));
    assertFalse(Files.exists(outputDirectory.resolve("BrokenP.xml")));
  }

  @Test
  void parseReportsUnexpectedEndOfInput() throws IOException {
    Path source = write("Broken.jack", "class Main {");
    Path outputDirectory = tempDir.resolve("out");

    Execution result =
        execute("parse", source.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(1, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Syntax error"));
    assertTrue(result.stderr().contains("line 1, column 13"));
    assertTrue(result.stderr().contains("EOF"));
    assertFalse(Files.exists(outputDirectory.resolve("BrokenP.xml")));
  }

  @Test
  void parseStillReportsLexicalErrorsWithPosition() throws IOException {
    Path source = write("Broken.jack", "class Main {}\n@");
    Path outputDirectory = tempDir.resolve("out");

    Execution result =
        execute("parse", source.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(1, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Lexical error"));
    assertTrue(result.stderr().contains("line 2, column 1"));
    assertFalse(Files.exists(outputDirectory.resolve("BrokenP.xml")));
  }

  @Test
  void parseRejectsOutputCollisionWithoutReplacingContent() throws IOException {
    Path source = write("Main.jack", "class Main {}");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Path output = Files.writeString(outputDirectory.resolve("MainP.xml"), "protected");

    Execution result =
        execute("parse", source.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(1, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertEquals("protected", Files.readString(output));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void parseRejectsMissingInputAsArgumentError() {
    Execution result = execute("parse");

    assertEquals(2, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Parse requires an input path"));
    assertTrue(result.stderr().contains("Usage:"));
  }

  @Test
  void parseRejectsUnknownOptionAsArgumentError() {
    Execution result = execute("parse", "Main.jack", "--unknown");

    assertEquals(2, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Unknown option"));
  }

  @Test
  void parseRejectsMissingSourceAsProcessingFailure() {
    Path missing = tempDir.resolve("Missing.jack");

    Execution result =
        execute("parse", missing.toString(), "--output-dir", tempDir.resolve("out").toString());

    assertEquals(1, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertTrue(result.stderr().contains(missing.toString()));
  }

  @Test
  void parseStopsAfterFirstInvalidSourceWithoutPublishingLaterOutputs() throws IOException {
    write("A.jack", "class A {}");
    write("B.jack", "class B {");
    write("C.jack", "class C {}");
    Path outputDirectory = tempDir.resolve("out");

    Execution result =
        execute("parse", tempDir.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(1, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Syntax error"));
    assertTrue(Files.isRegularFile(outputDirectory.resolve("AP.xml")));
    assertFalse(Files.exists(outputDirectory.resolve("BP.xml")));
    assertFalse(Files.exists(outputDirectory.resolve("CP.xml")));
  }

  private Path write(String name, String source) throws IOException {
    return Files.writeString(tempDir.resolve(name), source, StandardCharsets.UTF_8);
  }

  private static Execution execute(String... arguments) {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
      int status = JackCompiler.run(arguments, out, err);

      return new Execution(
          status, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }
  }

  private record Execution(int status, String stdout, String stderr) {}
}
