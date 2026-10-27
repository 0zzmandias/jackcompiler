package jackcompiler.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the executable JAR built during Maven's package phase, in a separate JVM. */
class JarExecutionIT {

  private static final Path JAR =
      Path.of("target", "jackcompiler.jar").toAbsolutePath().normalize();

  @TempDir Path tempDir;

  @Test
  void packagedJarHasExpectedMainClass() throws IOException {
    assertTrue(Files.isRegularFile(JAR), "Packaged JAR must exist before integration tests");

    try (JarFile file = new JarFile(JAR.toFile())) {
      assertNotNull(file.getManifest());
      assertEquals(
          "jackcompiler.cli.JackCompiler",
          file.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS));
      assertNotNull(file.getJarEntry("jackcompiler/cli/JackCompiler.class"));
    }
  }

  @Test
  void packagedJarSupportsHelp() throws Exception {
    Execution result = execute(tempDir, "--help");

    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertTrue(result.stdout().contains("tokenize"));
    assertTrue(result.stdout().contains("parse"));
    assertTrue(result.stdout().contains("--output-dir"));
  }

  @Test
  void packagedJarMatchesOfficialSquareReference() throws Exception {
    Path source = tempDir.resolve("Main.jack");
    byte[] original = resource("Square", "Main.jack");
    Files.write(source, original);
    Path outputDirectory = tempDir.resolve("output");

    Execution result =
        execute(tempDir, "tokenize", source.toString(), "--output-dir", outputDirectory.toString());

    Path output = outputDirectory.resolve("MainT.xml");
    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals("Generated: " + output + System.lineSeparator(), result.stdout());
    assertEquals(
        normalizeLineEndings(new String(resource("Square", "MainT.xml"), StandardCharsets.UTF_8)),
        Files.readString(output, StandardCharsets.UTF_8));
    assertArrayEquals(original, Files.readAllBytes(source));
  }

  @Test
  void packagedJarUsesDefaultDirectoryRelativeToItsWorkingDirectory() throws Exception {
    Path workingDirectory = Files.createDirectory(tempDir.resolve("working"));
    Files.writeString(workingDirectory.resolve("Main.jack"), "class Main {}\n");

    Execution result = execute(workingDirectory, "tokenize", "Main.jack");

    Path expected = workingDirectory.resolve("target/generated-tokens/MainT.xml");
    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals("Generated: " + expected + System.lineSeparator(), result.stdout());
    assertTrue(Files.readString(expected).contains("<identifier> Main </identifier>"));
  }

  @Test
  void packagedJarSortsDirectoryInputsWithoutRecursion() throws Exception {
    Path sources = Files.createDirectory(tempDir.resolve("sources"));
    Files.writeString(sources.resolve("Zebra.jack"), "class Zebra {}");
    Files.writeString(sources.resolve("Alpha.jack"), "class Alpha {}");
    Path nested = Files.createDirectory(sources.resolve("nested"));
    Files.writeString(nested.resolve("Nested.jack"), "class Nested {}");
    Path destination = tempDir.resolve("out");

    Execution result =
        execute(tempDir, "tokenize", sources.toString(), "--output-dir", destination.toString());

    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals(
        "Generated: "
            + destination.resolve("AlphaT.xml")
            + System.lineSeparator()
            + "Generated: "
            + destination.resolve("ZebraT.xml")
            + System.lineSeparator(),
        result.stdout());
    assertFalse(Files.exists(destination.resolve("NestedT.xml")));
  }

  @Test
  void packagedJarReportsLexicalErrorWithSourcePosition() throws Exception {
    Path source = Files.writeString(tempDir.resolve("Invalid.jack"), "class Main {}\n@");
    Path destination = tempDir.resolve("out");

    Execution result =
        execute(tempDir, "tokenize", source.toString(), "--output-dir", destination.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains(source.toString()));
    assertTrue(result.stderr().contains("line 2, column 1"));
    assertFalse(Files.exists(destination.resolve("InvalidT.xml")));
  }

  @Test
  void packagedJarRejectsMalformedUtf8() throws Exception {
    Path source = tempDir.resolve("Malformed.jack");
    byte[] invalid = {(byte) 0xC3, (byte) 0x28};
    Files.write(source, invalid);

    Execution result =
        execute(
            tempDir,
            "tokenize",
            source.toString(),
            "--output-dir",
            tempDir.resolve("out").toString());

    assertEquals(1, result.exitCode());
    assertTrue(result.stderr().contains("I/O error"));
    assertTrue(result.stderr().contains("Malformed.jack"));
    assertFalse(result.stderr().contains("line 1, column"));
    assertArrayEquals(invalid, Files.readAllBytes(source));
  }

  @Test
  void packagedJarRejectsCustomOutputCollisionWithoutOverwriting() throws Exception {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Path output = Files.writeString(outputDirectory.resolve("MainT.xml"), "original");

    Execution result =
        execute(tempDir, "tokenize", source.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertEquals("original", Files.readString(output));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void packagedJarStopsAtFirstSourceFailure() throws Exception {
    Path sources = Files.createDirectory(tempDir.resolve("sources"));
    Files.writeString(sources.resolve("A.jack"), "class A {}");
    Files.writeString(sources.resolve("B.jack"), "@");
    Files.writeString(sources.resolve("C.jack"), "class C {}");
    Path destination = tempDir.resolve("out");

    Execution result =
        execute(tempDir, "tokenize", sources.toString(), "--output-dir", destination.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(Files.isRegularFile(destination.resolve("AT.xml")));
    assertFalse(Files.exists(destination.resolve("BT.xml")));
    assertFalse(Files.exists(destination.resolve("CT.xml")));
  }

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
  void packagedJarSupportsBothCommandsForSameSource() throws Exception {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");
    Path destination = tempDir.resolve("out");

    Execution tokens =
        execute(tempDir, "tokenize", source.toString(), "--output-dir", destination.toString());
    Execution syntax =
        execute(tempDir, "parse", source.toString(), "--output-dir", destination.toString());

    assertEquals(0, tokens.exitCode());
    assertEquals(0, syntax.exitCode());
    assertEquals("", tokens.stderr());
    assertEquals("", syntax.stderr());
    assertTrue(Files.readString(destination.resolve("MainT.xml")).startsWith("<tokens>\n"));
    assertTrue(Files.readString(destination.resolve("MainP.xml")).startsWith("<class>\n"));
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

  private Execution execute(Path workingDirectory, String... arguments)
      throws IOException, InterruptedException {
    Path stdout = Files.createTempFile(tempDir, "jar-stdout-", ".txt");
    Path stderr = Files.createTempFile(tempDir, "jar-stderr-", ".txt");

    List<String> command = new ArrayList<>();
    command.add(javaExecutable().toString());
    command.add("-jar");
    command.add(JAR.toString());
    command.addAll(List.of(arguments));

    Process process =
        new ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile())
            .start();

    try {
      assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Packaged JAR process timed out");
      return new Execution(
          process.exitValue(),
          Files.readString(stdout, StandardCharsets.UTF_8),
          Files.readString(stderr, StandardCharsets.UTF_8));
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  private static Path javaExecutable() {
    String name =
        System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
            ? "java.exe"
            : "java";
    return Path.of(System.getProperty("java.home"), "bin", name);
  }

  private static byte[] resource(String suite, String name) throws IOException {
    String path = "/nand2tetris/" + suite + "/" + name;
    try (InputStream stream = JarExecutionIT.class.getResourceAsStream(path)) {
      if (stream == null) {
        throw new IOException("Official reference missing: " + path);
      }
      return stream.readAllBytes();
    }
  }

  private static String normalizeLineEndings(String text) {
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private record Execution(int exitCode, String stdout, String stderr) {}
}
