package jackcompiler.cli;

import static jackcompiler.cli.CliIntegrationTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.cli.CliIntegrationTestSupport.Execution;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies shared CLI contracts and interactions between supported operations. */
class CliIntegrationTest {

  @TempDir Path tempDir;

  @Test
  void helpSucceedsAndWritesUsageToStdout() {
    Execution result = execute("--help");

    assertEquals(0, result.status());
    assertTrue(result.stdout().contains("tokenize"));
    assertTrue(result.stdout().contains("parse"));
    assertTrue(result.stdout().contains("compile"));
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
  void rejectsExtraPositionalArguments() {
    Execution result = execute("tokenize", "Main.jack", "extra");

    assertEquals(2, result.status());
    assertTrue(result.stderr().contains("Unexpected argument"));
    assertEquals("", result.stdout());
  }

  @ParameterizedTest
  @ValueSource(strings = {"tokens", "Tokenize", "help"})
  void rejectsUnknownCommands(String command) {
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
  void allThreeCommandsCanProcessSameValidSource() throws IOException {
    Path source = write("Main.jack", "class Main { function void run() { return; } }");
    Path out = tempDir.resolve("out");

    Execution tokens = execute("tokenize", source.toString(), "--output-dir", out.toString());
    Execution syntax = execute("parse", source.toString(), "--output-dir", out.toString());
    Execution vm = execute("compile", source.toString(), "--output-dir", out.toString());

    assertEquals(0, tokens.status());
    assertEquals(0, syntax.status());
    assertEquals(0, vm.status());
    assertEquals("", tokens.stderr());
    assertEquals("", syntax.stderr());
    assertEquals("", vm.stderr());
    assertTrue(Files.isRegularFile(out.resolve("MainT.xml")));
    assertTrue(Files.isRegularFile(out.resolve("MainP.xml")));
    assertTrue(Files.isRegularFile(out.resolve("Main.vm")));
  }

  @Test
  void tokenizeAndParseRemainNonRecursiveWhileCompileRecurses() throws IOException {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    Files.writeString(root.resolve("Main.jack"), "class Main {}");
    Path nested = Files.createDirectory(root.resolve("nested"));
    Files.writeString(nested.resolve("Other.jack"), "class Other {}");

    Path tokensOut = tempDir.resolve("tokens");
    Path syntaxOut = tempDir.resolve("syntax");
    Path vmOut = tempDir.resolve("vm");

    Execution tokens = execute("tokenize", root.toString(), "--output-dir", tokensOut.toString());
    Execution syntax = execute("parse", root.toString(), "--output-dir", syntaxOut.toString());
    Execution vm = execute("compile", root.toString(), "--output-dir", vmOut.toString());

    assertEquals(0, tokens.status());
    assertEquals(0, syntax.status());
    assertEquals(0, vm.status());
    assertTrue(Files.exists(tokensOut.resolve("MainT.xml")));
    assertFalse(Files.exists(tokensOut.resolve("OtherT.xml")));
    assertTrue(Files.exists(syntaxOut.resolve("MainP.xml")));
    assertFalse(Files.exists(syntaxOut.resolve("OtherP.xml")));
    assertTrue(Files.exists(vmOut.resolve("Main.vm")));
    assertTrue(Files.exists(vmOut.resolve("nested/Other.vm")));
  }

  private Path write(String name, String source) throws IOException {
    return CliIntegrationTestSupport.write(tempDir, name, source);
  }
}
