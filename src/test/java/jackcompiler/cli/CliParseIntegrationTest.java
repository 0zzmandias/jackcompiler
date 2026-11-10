package jackcompiler.cli;

import static jackcompiler.cli.CliIntegrationTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.cli.CliIntegrationTestSupport.Execution;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the parse operation through the CLI entry point. */
class CliParseIntegrationTest {

  @TempDir Path tempDir;

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
    return CliIntegrationTestSupport.write(tempDir, name, source);
  }
}
