package jackcompiler.integration;

import static jackcompiler.integration.JarExecutionTestSupport.assertNoTemporaryFiles;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.integration.JarExecutionTestSupport.Execution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies executable-JAR metadata and behavior shared across CLI operations. */
class JarExecutionIT {

  private static final Path JAR = JarExecutionTestSupport.JAR;

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
      assertNotNull(file.getJarEntry("jackcompiler/application/CompileCommand.class"));
    }
  }

  @Test
  void packagedJarSupportsHelp() throws Exception {
    Execution result = execute(tempDir, "--help");

    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertTrue(result.stdout().contains("tokenize"));
    assertTrue(result.stdout().contains("parse"));
    assertTrue(result.stdout().contains("compile"));
    assertTrue(result.stdout().contains("--output-dir"));
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

  @Test
  void packagedJarSupportsAllThreeOperationsForOneSource() throws Exception {
    Path source =
        Files.writeString(
            tempDir.resolve("Main.jack"), "class Main { function void run() { return; } }");
    Path out = tempDir.resolve("all-output");

    Execution tokens =
        execute(tempDir, "tokenize", source.toString(), "--output-dir", out.toString());
    Execution syntax = execute(tempDir, "parse", source.toString(), "--output-dir", out.toString());
    Execution vm = execute(tempDir, "compile", source.toString(), "--output-dir", out.toString());

    assertEquals(0, tokens.exitCode());
    assertEquals(0, syntax.exitCode());
    assertEquals(0, vm.exitCode());
    assertEquals("", tokens.stderr());
    assertEquals("", syntax.stderr());
    assertEquals("", vm.stderr());
    assertTrue(Files.isRegularFile(out.resolve("MainT.xml")));
    assertTrue(Files.isRegularFile(out.resolve("MainP.xml")));
    assertTrue(Files.isRegularFile(out.resolve("Main.vm")));
  }

  private Execution execute(Path workingDirectory, String... arguments)
      throws IOException, InterruptedException {
    return JarExecutionTestSupport.execute(tempDir, workingDirectory, arguments);
  }
}
