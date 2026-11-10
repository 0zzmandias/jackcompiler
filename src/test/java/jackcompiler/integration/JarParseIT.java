package jackcompiler.integration;

import static jackcompiler.integration.JarExecutionTestSupport.assertNoTemporaryFiles;
import static jackcompiler.integration.JarExecutionTestSupport.resource;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.integration.JarExecutionTestSupport.Execution;
import jackcompiler.lexer.JackTokenizer;
import jackcompiler.parser.JackParser;
import jackcompiler.xml.ParseTreeXmlWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Tests parsing through the packaged executable JAR in a separate JVM. */
class JarParseIT {

  @TempDir Path tempDir;

  @Test
  void packagedJarParsesSourceIntoCustomDirectory() throws Exception {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");
    byte[] original = Files.readAllBytes(source);
    Path outputDirectory = tempDir.resolve("parser-out");

    Execution result =
        execute(tempDir, "parse", source.toString(), "--output-dir", outputDirectory.toString());

    Path output = outputDirectory.resolve("MainP.xml");
    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals("Generated: " + output + System.lineSeparator(), result.stdout());
    assertEquals(
        "<class>\n"
            + "  <keyword> class </keyword>\n"
            + "  <identifier> Main </identifier>\n"
            + "  <symbol> { </symbol>\n"
            + "  <symbol> } </symbol>\n"
            + "</class>\n",
        Files.readString(output, StandardCharsets.UTF_8));
    assertArrayEquals(original, Files.readAllBytes(source));
  }

  @Test
  void packagedJarUsesParserDefaultDirectoryRelativeToWorkingDirectory() throws Exception {
    Path workingDirectory = Files.createDirectory(tempDir.resolve("parser-working"));
    Files.writeString(workingDirectory.resolve("Main.jack"), "class Main {}\n");

    Execution result = execute(workingDirectory, "parse", "Main.jack");

    Path expected = workingDirectory.resolve("target/generated-parser/MainP.xml");
    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals("Generated: " + expected + System.lineSeparator(), result.stdout());
    assertTrue(Files.readString(expected).startsWith("<class>\n"));
    assertFalse(Files.exists(workingDirectory.resolve("target/generated-tokens/MainT.xml")));
  }

  @Test
  void packagedJarParsesDirectoryInDeterministicOrder() throws Exception {
    Path sources = Files.createDirectory(tempDir.resolve("parser-sources"));
    Files.writeString(sources.resolve("Zebra.jack"), "class Zebra {}");
    Files.writeString(sources.resolve("Alpha.jack"), "class Alpha {}");
    Path nested = Files.createDirectory(sources.resolve("nested"));
    Files.writeString(nested.resolve("Hidden.jack"), "class Hidden {}");
    Path destination = tempDir.resolve("out");

    Execution result =
        execute(tempDir, "parse", sources.toString(), "--output-dir", destination.toString());

    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals(
        "Generated: "
            + destination.resolve("AlphaP.xml")
            + System.lineSeparator()
            + "Generated: "
            + destination.resolve("ZebraP.xml")
            + System.lineSeparator(),
        result.stdout());
    assertTrue(Files.isRegularFile(destination.resolve("AlphaP.xml")));
    assertTrue(Files.isRegularFile(destination.resolve("ZebraP.xml")));
    assertFalse(Files.exists(destination.resolve("HiddenP.xml")));
  }

  @Test
  void packagedJarReportsSyntaxErrorWithOriginalPosition() throws Exception {
    Path source =
        Files.writeString(
            tempDir.resolve("Broken.jack"),
            "class Main {\n" + "  function void run() {\n" + "    let x = ;\n" + "  }\n" + "}");
    Path destination = tempDir.resolve("out");

    Execution result =
        execute(tempDir, "parse", source.toString(), "--output-dir", destination.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Syntax error"));
    assertTrue(result.stderr().contains(source.toString()));
    assertTrue(result.stderr().contains("line 3, column 13"));
    assertTrue(result.stderr().contains("Expected term"));
    assertFalse(Files.exists(destination.resolve("BrokenP.xml")));
  }

  @Test
  void packagedJarReportsLexicalErrorDuringParsing() throws Exception {
    Path source = Files.writeString(tempDir.resolve("Invalid.jack"), "class Main {}\n@");
    Path destination = tempDir.resolve("out");

    Execution result =
        execute(tempDir, "parse", source.toString(), "--output-dir", destination.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Lexical error"));
    assertTrue(result.stderr().contains("line 2, column 1"));
    assertFalse(Files.exists(destination.resolve("InvalidP.xml")));
  }

  @Test
  void packagedJarRejectsParseOutputCollisionWithoutOverwriting() throws Exception {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Path output = Files.writeString(outputDirectory.resolve("MainP.xml"), "original");

    Execution result =
        execute(tempDir, "parse", source.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertEquals("original", Files.readString(output));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void packagedJarRejectsInvalidParseArguments() throws Exception {
    Execution result = execute(tempDir, "parse");

    assertEquals(2, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Parse requires an input path"));
    assertTrue(result.stderr().contains("Usage:"));
  }

  @ParameterizedTest(name = "official syntax: {0}/{1}")
  @MethodSource("officialPrograms")
  void packagedJarMatchesApprovedParserXmlForAllPrograms(String suite, String name)
      throws Exception {
    Path source = tempDir.resolve(name + ".jack");
    byte[] original = resource(suite, name + ".jack");
    Files.write(source, original);

    String sourceText = new String(original, StandardCharsets.UTF_8);
    String expected =
        new ParseTreeXmlWriter()
            .toXml(new JackParser().parse(new JackTokenizer().tokenize(sourceText)));

    Path directory = tempDir.resolve("parser-output");
    Path output = directory.resolve(name + "P.xml");

    Execution result =
        execute(tempDir, "parse", source.toString(), "--output-dir", directory.toString());

    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals("Generated: " + output + System.lineSeparator(), result.stdout());
    assertEquals(expected, Files.readString(output, StandardCharsets.UTF_8));
    assertArrayEquals(original, Files.readAllBytes(source));
    assertFalse(Files.exists(directory.resolve(name + "T.xml")));
  }

  @Test
  void packagedJarPreservesPreviousParserOutputAfterSyntaxFailure() throws Exception {
    Path workingDirectory = Files.createDirectory(tempDir.resolve("syntax-regeneration"));
    Path source = workingDirectory.resolve("Main.jack");
    Path output = workingDirectory.resolve("target/generated-parser/MainP.xml");

    Files.writeString(source, "class Main {}");

    Execution first = execute(workingDirectory, "parse", "Main.jack");

    assertEquals(0, first.exitCode());
    String previousXml = Files.readString(output);

    Files.writeString(source, "class Main {");

    Execution failure = execute(workingDirectory, "parse", "Main.jack");

    assertEquals(1, failure.exitCode());
    assertEquals("", failure.stdout());
    assertTrue(failure.stderr().contains("Syntax error"));
    assertEquals(previousXml, Files.readString(output));
    assertNoTemporaryFiles(output.getParent());
  }

  @Test
  void packagedJarStopsParsingAtFirstSyntaxFailure() throws Exception {
    Path sources = Files.createDirectory(tempDir.resolve("parser-batch"));
    Files.writeString(sources.resolve("A.jack"), "class A {}");
    Files.writeString(sources.resolve("B.jack"), "class B {");
    Files.writeString(sources.resolve("C.jack"), "class C {}");

    Path destination = tempDir.resolve("out");

    Execution result =
        execute(tempDir, "parse", sources.toString(), "--output-dir", destination.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Syntax error"));
    assertTrue(result.stderr().contains("B.jack"));
    assertTrue(Files.isRegularFile(destination.resolve("AP.xml")));
    assertFalse(Files.exists(destination.resolve("BP.xml")));
    assertFalse(Files.exists(destination.resolve("CP.xml")));
  }

  @Test
  void packagedJarRejectsMalformedUtf8DuringParsing() throws Exception {
    Path source = tempDir.resolve("Malformed.jack");
    byte[] malformed = {(byte) 0xC3, (byte) 0x28};
    Files.write(source, malformed);

    Path directory = tempDir.resolve("out");

    Execution result =
        execute(tempDir, "parse", source.toString(), "--output-dir", directory.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertTrue(result.stderr().contains("Malformed.jack"));
    assertFalse(result.stderr().contains("line 1, column"));
    assertFalse(Files.exists(directory.resolve("MalformedP.xml")));
    assertArrayEquals(malformed, Files.readAllBytes(source));
  }

  private static Stream<Arguments> officialPrograms() {
    return JarExecutionTestSupport.officialPrograms();
  }

  private Execution execute(Path workingDirectory, String... arguments)
      throws IOException, InterruptedException {
    return JarExecutionTestSupport.execute(tempDir, workingDirectory, arguments);
  }
}
