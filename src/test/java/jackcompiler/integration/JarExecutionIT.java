package jackcompiler.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.parser.JackParser;
import jackcompiler.xml.ParseTreeXmlWriter;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

  @ParameterizedTest(name = "official tokens: {0}/{1}")
  @MethodSource("officialPrograms")
  void packagedJarMatchesOfficialTokenXmlForAllPrograms(String suite, String name)
      throws Exception {
    Path source = tempDir.resolve(name + ".jack");
    byte[] original = resource(suite, name + ".jack");
    Files.write(source, original);

    Path directory = tempDir.resolve("token-output");
    Path output = directory.resolve(name + "T.xml");

    Execution result =
        execute(tempDir, "tokenize", source.toString(), "--output-dir", directory.toString());

    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals("Generated: " + output + System.lineSeparator(), result.stdout());
    assertEquals(
        normalizeLineEndings(new String(resource(suite, name + "T.xml"), StandardCharsets.UTF_8)),
        Files.readString(output, StandardCharsets.UTF_8));
    assertArrayEquals(original, Files.readAllBytes(source));
    assertFalse(Files.exists(directory.resolve(name + "P.xml")));
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

  @ParameterizedTest(name = "{0}: default output replacement and failure preservation")
  @ValueSource(strings = {"tokenize", "parse"})
  void packagedJarRegeneratesDefaultOutputAndPreservesItOnFailure(String command) throws Exception {
    Path workingDirectory = Files.createDirectory(tempDir.resolve("regeneration"));
    Path source = workingDirectory.resolve("Main.jack");

    String directoryName = command.equals("tokenize") ? "generated-tokens" : "generated-parser";
    String suffix = command.equals("tokenize") ? "T.xml" : "P.xml";

    Path output =
        workingDirectory.resolve("target").resolve(directoryName).resolve("Main" + suffix);

    Files.writeString(source, "class First {}");

    Execution first = execute(workingDirectory, command, "Main.jack");

    assertEquals(0, first.exitCode());
    assertEquals("", first.stderr());
    assertEquals("Generated: " + output + System.lineSeparator(), first.stdout());
    assertTrue(Files.readString(output).contains("<identifier> First </identifier>"));

    Files.writeString(source, "class Second {}");

    Execution second = execute(workingDirectory, command, "Main.jack");

    assertEquals(0, second.exitCode());
    assertEquals("", second.stderr());
    assertEquals("Generated: " + output + System.lineSeparator(), second.stdout());

    String previousXml = Files.readString(output);
    assertTrue(previousXml.contains("<identifier> Second </identifier>"));
    assertFalse(previousXml.contains("<identifier> First </identifier>"));

    Files.writeString(source, "class Broken { @ }");

    Execution failure = execute(workingDirectory, command, "Main.jack");

    assertEquals(1, failure.exitCode());
    assertEquals("", failure.stdout());
    assertTrue(failure.stderr().contains("Lexical error"));
    assertEquals(previousXml, Files.readString(output));
    assertEquals("class Broken { @ }", Files.readString(source));
    assertNoTemporaryFiles(output.getParent());
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

  @ParameterizedTest(name = "{0}: invalid XML character")
  @ValueSource(strings = {"tokenize", "parse"})
  void packagedJarRejectsInvalidXmlCharactersWithoutPublishingOutput(String command)
      throws Exception {
    String sourceText =
        "class Main {"
            + " function void run() {"
            + " do Output.printString(\"a"
            + ((char) 1)
            + "b\");"
            + " return;"
            + " }"
            + "}";

    Path source = Files.writeString(tempDir.resolve("Main.jack"), sourceText);
    Path directory = tempDir.resolve("out");
    String suffix = command.equals("tokenize") ? "T.xml" : "P.xml";

    Execution result =
        execute(tempDir, command, source.toString(), "--output-dir", directory.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Processing error"));
    assertTrue(result.stderr().contains("Invalid XML character"));
    assertFalse(Files.exists(directory.resolve("Main" + suffix)));
    assertEquals(sourceText, Files.readString(source));
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

  @ParameterizedTest(name = "{0}: symbolic-link output file")
  @ValueSource(strings = {"tokenize", "parse"})
  void packagedJarRejectsSymbolicLinkOutputWithoutChangingTarget(String command) throws Exception {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");
    Path directory = Files.createDirectory(tempDir.resolve("out"));
    Path protectedFile = Files.writeString(tempDir.resolve("protected.xml"), "protected");
    String suffix = command.equals("tokenize") ? "T.xml" : "P.xml";

    Files.createSymbolicLink(directory.resolve("Main" + suffix), protectedFile);

    Execution result =
        execute(tempDir, command, source.toString(), "--output-dir", directory.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertEquals("protected", Files.readString(protectedFile));
    assertEquals("class Main {}", Files.readString(source));
  }

  @ParameterizedTest(name = "{0}: symbolic-link output directory")
  @ValueSource(strings = {"tokenize", "parse"})
  void packagedJarRejectsSymbolicLinkOutputDirectory(String command) throws Exception {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");
    Path realDirectory = Files.createDirectory(tempDir.resolve("real"));
    Path linkedDirectory = tempDir.resolve("linked");
    String suffix = command.equals("tokenize") ? "T.xml" : "P.xml";

    Files.createSymbolicLink(linkedDirectory, realDirectory);

    Execution result =
        execute(tempDir, command, source.toString(), "--output-dir", linkedDirectory.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertFalse(Files.exists(realDirectory.resolve("Main" + suffix)));
    assertEquals("class Main {}", Files.readString(source));
  }

  @ParameterizedTest(name = "{0}: hard-link source alias")
  @ValueSource(strings = {"tokenize", "parse"})
  void packagedJarRejectsHardLinkAliasToSource(String command) throws Exception {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");
    Path directory = Files.createDirectory(tempDir.resolve("out"));
    String suffix = command.equals("tokenize") ? "T.xml" : "P.xml";

    Files.createLink(directory.resolve("Main" + suffix), source);

    Execution result =
        execute(tempDir, command, source.toString(), "--output-dir", directory.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertTrue(result.stderr().contains("aliases the source"));
    assertEquals("class Main {}", Files.readString(source));
  }

  @ParameterizedTest(name = "{0}: protected reference directory")
  @ValueSource(strings = {"tokenize", "parse"})
  void packagedJarRejectsOutputInsideProtectedReferenceDirectory(String command) throws Exception {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");
    Path directory = tempDir.resolve("src/test/resources/nand2tetris");
    String suffix = command.equals("tokenize") ? "T.xml" : "P.xml";

    Execution result =
        execute(tempDir, command, source.toString(), "--output-dir", directory.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertFalse(Files.exists(directory.resolve("Main" + suffix)));
    assertEquals("class Main {}", Files.readString(source));
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

  @Test
  void packagedJarDistinguishesEmptyLexicalAndSyntacticInput() throws Exception {
    Path source = Files.writeString(tempDir.resolve("Empty.jack"), "");
    Path directory = tempDir.resolve("out");

    Execution tokens =
        execute(tempDir, "tokenize", source.toString(), "--output-dir", directory.toString());

    Execution syntax =
        execute(tempDir, "parse", source.toString(), "--output-dir", directory.toString());

    assertEquals(0, tokens.exitCode());
    assertEquals("", tokens.stderr());
    assertEquals("<tokens>\n</tokens>\n", Files.readString(directory.resolve("EmptyT.xml")));

    assertEquals(1, syntax.exitCode());
    assertEquals("", syntax.stdout());
    assertTrue(syntax.stderr().contains("Syntax error"));
    assertFalse(Files.exists(directory.resolve("EmptyP.xml")));
  }

  @Test
  void packagedJarHandlesUnicodeAndSpacesInWorkingPaths() throws Exception {
    Path workingDirectory = Files.createDirectory(tempDir.resolve("pasta com espaços"));
    Path source = Files.writeString(workingDirectory.resolve("Olá Mundo.jack"), "class Main {}");
    byte[] original = Files.readAllBytes(source);
    Path directory = workingDirectory.resolve("saídas XML");

    Execution tokens =
        execute(
            workingDirectory, "tokenize", source.toString(), "--output-dir", directory.toString());

    Execution syntax =
        execute(workingDirectory, "parse", source.toString(), "--output-dir", directory.toString());

    assertEquals(0, tokens.exitCode());
    assertEquals(0, syntax.exitCode());
    assertEquals("", tokens.stderr());
    assertEquals("", syntax.stderr());

    assertTrue(Files.readString(directory.resolve("Olá MundoT.xml")).startsWith("<tokens>\n"));
    assertTrue(Files.readString(directory.resolve("Olá MundoP.xml")).startsWith("<class>\n"));
    assertArrayEquals(original, Files.readAllBytes(source));
  }

  private static Stream<Arguments> officialPrograms() {
    return Stream.of(
        Arguments.of("Square", "Main"),
        Arguments.of("Square", "Square"),
        Arguments.of("Square", "SquareGame"),
        Arguments.of("ExpressionLessSquare", "Main"),
        Arguments.of("ExpressionLessSquare", "Square"),
        Arguments.of("ExpressionLessSquare", "SquareGame"),
        Arguments.of("ArrayTest", "Main"));
  }

  private static void assertNoTemporaryFiles(Path directory) throws IOException {
    try (Stream<Path> entries = Files.list(directory)) {
      assertEquals(
          0L,
          entries
              .filter(path -> path.getFileName().toString().startsWith(".jackcompiler-"))
              .count());
    }
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
