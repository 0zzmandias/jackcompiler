package jackcompiler.cli;

import static jackcompiler.cli.CliIntegrationTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.cli.CliIntegrationTestSupport.Execution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the tokenize operation through the CLI entry point. */
class CliTokenizeIntegrationTest {

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
  void rejectsMissingInput() {
    Execution result = execute("tokenize");

    assertEquals(2, result.status());
    assertTrue(result.stderr().contains("input path"));
    assertEquals("", result.stdout());
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

  private Path write(String name, String source) throws IOException {
    return CliIntegrationTestSupport.write(tempDir, name, source);
  }
}
