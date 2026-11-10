package jackcompiler.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.cli.CliArguments.Command;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CliArgumentParserTest {

  private final CliArgumentParser parser = new CliArgumentParser();

  @Test
  void parsesStandaloneHelpWithoutInput() {
    CliArguments result = parser.parse(new String[] {"--help"});

    assertEquals(Command.HELP, result.command());
    assertTrue(result.input().isEmpty());
    assertTrue(result.outputDirectory().isEmpty());
  }

  @Test
  void parsesTokenizeWithSourceFile() {
    CliArguments result = parser.parse(new String[] {"tokenize", "Main.jack"});

    assertEquals(Command.TOKENIZE, result.command());
    assertEquals(Path.of("Main.jack").toAbsolutePath().normalize(), result.input().orElseThrow());
    assertTrue(result.outputDirectory().isEmpty());
  }

  @Test
  void parsesTokenizeWithDirectory() {
    CliArguments result = parser.parse(new String[] {"tokenize", "examples"});

    assertEquals(Path.of("examples").toAbsolutePath().normalize(), result.input().orElseThrow());
  }

  @Test
  void parsesCustomOutputDirectory() {
    CliArguments result =
        parser.parse(new String[] {"tokenize", "Main.jack", "--output-dir", "results"});

    assertEquals(
        Path.of("results").toAbsolutePath().normalize(), result.outputDirectory().orElseThrow());
  }

  @Test
  void normalizesBothPaths() {
    CliArguments result =
        parser.parse(new String[] {"tokenize", "a/../Main.jack", "--output-dir", "a/../out"});

    assertEquals(Path.of("Main.jack").toAbsolutePath().normalize(), result.input().orElseThrow());
    assertEquals(
        Path.of("out").toAbsolutePath().normalize(), result.outputDirectory().orElseThrow());
  }

  @Test
  void keepsSpacesAndUnicodeInPaths() {
    CliArguments result =
        parser.parse(
            new String[] {"tokenize", "Meus Arquivos/Olá.jack", "--output-dir", "Pasta de saída"});

    assertEquals(
        Path.of("Meus Arquivos/Olá.jack").toAbsolutePath().normalize(),
        result.input().orElseThrow());
    assertEquals(
        Path.of("Pasta de saída").toAbsolutePath().normalize(),
        result.outputDirectory().orElseThrow());
  }

  @Test
  void doesNotMutateArgumentArray() {
    String[] input = {"tokenize", "Main.jack", "--output-dir", "out"};
    String[] before = input.clone();

    parser.parse(input);

    assertArrayEquals(before, input);
  }

  @Test
  void rejectsMissingCommand() {
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> parser.parse(new String[0]));

    assertTrue(error.getMessage().contains("Usage:"));
  }

  @Test
  void rejectsNullArgumentArray() {
    assertThrows(NullPointerException.class, () -> parser.parse(null));
  }

  @Test
  void rejectsNullCommand() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class, () -> parser.parse(new String[] {null, "Main.jack"}));

    assertTrue(error.getMessage().contains("Command cannot be null"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"tokens", "Tokenize", "", "help"})
  void rejectsUnknownCommands(String command) {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {command, "Main.jack"}));

    assertTrue(error.getMessage().contains("Unknown command"));
  }

  @Test
  void rejectsTokenizeWithoutInput() {
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> parser.parse(new String[] {"tokenize"}));

    assertTrue(error.getMessage().contains("input path"));
  }

  @Test
  void rejectsNullInput() {
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse(new String[] {"tokenize", null}));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", "--output-dir", "--help"})
  void rejectsMissingOrOptionLikeInput(String input) {
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse(new String[] {"tokenize", input}));
  }

  @Test
  void rejectsHelpWithExtraArgument() {
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse(new String[] {"--help", "Main.jack"}));
  }

  @Test
  void rejectsAdditionalPositionalArgument() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"tokenize", "Main.jack", "extra"}));

    assertTrue(error.getMessage().contains("Unexpected argument"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"--unknown", "--help", "-v"})
  void rejectsUnknownOptions(String option) {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"tokenize", "Main.jack", option}));

    assertTrue(error.getMessage().contains("Unknown option"));
  }

  @Test
  void rejectsRepeatedOutputDirectoryOption() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                parser.parse(
                    new String[] {
                      "tokenize", "Main.jack", "--output-dir", "out", "--output-dir", "elsewhere"
                    }));

    assertTrue(error.getMessage().contains("Duplicate"));
  }

  @Test
  void rejectsOutputOptionWithoutDirectory() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"tokenize", "Main.jack", "--output-dir"}));

    assertTrue(error.getMessage().contains("Missing directory"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", "--help", "--output-dir"})
  void rejectsBlankOrOptionLikeOutputDirectory(String output) {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(new String[] {"tokenize", "Main.jack", "--output-dir", output}));
  }

  @Test
  void rejectsNullOutputDirectory() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(new String[] {"tokenize", "Main.jack", "--output-dir", null}));
  }

  @Test
  void rejectsInvalidInputPath() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"tokenize", "bad\0path"}));

    assertInstanceOf(InvalidPathException.class, error.getCause());
    assertTrue(error.getMessage().contains("Invalid input path"));
  }

  @Test
  void rejectsInvalidOutputDirectoryPath() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                parser.parse(new String[] {"tokenize", "Main.jack", "--output-dir", "bad\0path"}));

    assertInstanceOf(InvalidPathException.class, error.getCause());
    assertTrue(error.getMessage().contains("Invalid output directory path"));
  }

  @Test
  void rejectsNullTrailingArgument() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"tokenize", "Main.jack", null}));

    assertTrue(error.getMessage().contains("Unexpected argument"));
  }

  @Test
  void includesAllSupportedFormsInUsage() {
    assertTrue(CliArgumentParser.USAGE.contains("tokenize"));
    assertTrue(CliArgumentParser.USAGE.contains("parse"));
    assertTrue(CliArgumentParser.USAGE.contains("compile"));
    assertTrue(CliArgumentParser.USAGE.contains("--help"));
    assertTrue(CliArgumentParser.USAGE.contains("--output-dir"));
    assertTrue(CliArgumentParser.USAGE.contains("jackcompiler compile "));
  }

  @Test
  void parsesParseWithSourceFile() {
    CliArguments result = parser.parse(new String[] {"parse", "Main.jack"});

    assertEquals(Command.PARSE, result.command());
    assertEquals(Path.of("Main.jack").toAbsolutePath().normalize(), result.input().orElseThrow());
    assertTrue(result.outputDirectory().isEmpty());
  }

  @Test
  void parsesParseWithDirectory() {
    CliArguments result = parser.parse(new String[] {"parse", "examples"});

    assertEquals(Command.PARSE, result.command());
    assertEquals(Path.of("examples").toAbsolutePath().normalize(), result.input().orElseThrow());
    assertTrue(result.outputDirectory().isEmpty());
  }

  @Test
  void parsesParseWithCustomOutputDirectory() {
    CliArguments result =
        parser.parse(new String[] {"parse", "Main.jack", "--output-dir", "results"});

    assertEquals(Command.PARSE, result.command());
    assertEquals(
        Path.of("results").toAbsolutePath().normalize(), result.outputDirectory().orElseThrow());
  }

  @Test
  void normalizesParseInputAndOutputPaths() {
    CliArguments result =
        parser.parse(new String[] {"parse", "a/../Main.jack", "--output-dir", "a/../out"});

    assertEquals(Path.of("Main.jack").toAbsolutePath().normalize(), result.input().orElseThrow());
    assertEquals(
        Path.of("out").toAbsolutePath().normalize(), result.outputDirectory().orElseThrow());
  }

  @Test
  void preservesSpacesAndUnicodeForParse() {
    CliArguments result =
        parser.parse(
            new String[] {"parse", "Meus Arquivos/Olá.jack", "--output-dir", "Pasta de saída"});

    assertEquals(Command.PARSE, result.command());
    assertEquals(
        Path.of("Meus Arquivos/Olá.jack").toAbsolutePath().normalize(),
        result.input().orElseThrow());
    assertEquals(
        Path.of("Pasta de saída").toAbsolutePath().normalize(),
        result.outputDirectory().orElseThrow());
  }

  @Test
  void doesNotMutateParseArgumentArray() {
    String[] input = {"parse", "Main.jack", "--output-dir", "out"};
    String[] before = input.clone();

    parser.parse(input);

    assertArrayEquals(before, input);
  }

  @Test
  void rejectsParseWithoutInput() {
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> parser.parse(new String[] {"parse"}));

    assertTrue(error.getMessage().contains("Parse requires an input path"));
    assertTrue(error.getMessage().contains("Usage:"));
  }

  @Test
  void rejectsNullParseInput() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse(new String[] {"parse", null}));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", "--output-dir", "--help"})
  void rejectsMissingOrOptionLikeParseInput(String input) {
    assertThrows(IllegalArgumentException.class, () -> parser.parse(new String[] {"parse", input}));
  }

  @Test
  void rejectsAdditionalParsePositionalArgument() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"parse", "Main.jack", "extra"}));

    assertTrue(error.getMessage().contains("Unexpected argument"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"--unknown", "--help", "-v"})
  void rejectsUnknownParseOptions(String option) {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"parse", "Main.jack", option}));

    assertTrue(error.getMessage().contains("Unknown option"));
  }

  @Test
  void rejectsRepeatedParseOutputDirectoryOption() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                parser.parse(
                    new String[] {
                      "parse", "Main.jack", "--output-dir", "out", "--output-dir", "elsewhere"
                    }));

    assertTrue(error.getMessage().contains("Duplicate"));
  }

  @Test
  void rejectsParseOutputOptionWithoutDirectory() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"parse", "Main.jack", "--output-dir"}));

    assertTrue(error.getMessage().contains("Missing directory"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", "--help", "--output-dir"})
  void rejectsBlankOrOptionLikeParseOutputDirectory(String output) {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(new String[] {"parse", "Main.jack", "--output-dir", output}));
  }

  @Test
  void rejectsNullParseOutputDirectory() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(new String[] {"parse", "Main.jack", "--output-dir", null}));
  }

  @Test
  void rejectsInvalidParseInputPath() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"parse", "bad\0path"}));

    assertInstanceOf(InvalidPathException.class, error.getCause());
    assertTrue(error.getMessage().contains("Invalid input path"));
  }

  @Test
  void rejectsInvalidParseOutputDirectoryPath() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"parse", "Main.jack", "--output-dir", "bad\0path"}));

    assertInstanceOf(InvalidPathException.class, error.getCause());
    assertTrue(error.getMessage().contains("Invalid output directory path"));
  }

  @Test
  void rejectsNullTrailingParseArgument() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"parse", "Main.jack", null}));

    assertTrue(error.getMessage().contains("Unexpected argument"));
  }

  @Test
  void parsesCompileWithSourceFile() {
    CliArguments result = parser.parse(new String[] {"compile", "Main.jack"});

    assertEquals(Command.COMPILE, result.command());
    assertEquals(Path.of("Main.jack").toAbsolutePath().normalize(), result.input().orElseThrow());
    assertTrue(result.outputDirectory().isEmpty());
  }

  @Test
  void parsesCompileWithDirectory() {
    CliArguments result = parser.parse(new String[] {"compile", "examples"});

    assertEquals(Command.COMPILE, result.command());
    assertEquals(Path.of("examples").toAbsolutePath().normalize(), result.input().orElseThrow());
  }

  @Test
  void parsesCompileWithCustomOutputDirectory() {
    CliArguments result =
        parser.parse(new String[] {"compile", "Main.jack", "--output-dir", "vm-out"});

    assertEquals(Command.COMPILE, result.command());
    assertEquals(
        Path.of("vm-out").toAbsolutePath().normalize(), result.outputDirectory().orElseThrow());
  }

  @Test
  void normalizesCompileInputAndOutputPaths() {
    CliArguments result =
        parser.parse(new String[] {"compile", "a/../Main.jack", "--output-dir", "a/../vm"});

    assertEquals(Path.of("Main.jack").toAbsolutePath().normalize(), result.input().orElseThrow());
    assertEquals(
        Path.of("vm").toAbsolutePath().normalize(), result.outputDirectory().orElseThrow());
  }

  @Test
  void preservesSpacesAndUnicodeForCompilePaths() {
    CliArguments result =
        parser.parse(
            new String[] {"compile", "Meus Arquivos/Olá.jack", "--output-dir", "Saída VM"});

    assertEquals(
        Path.of("Meus Arquivos/Olá.jack").toAbsolutePath().normalize(),
        result.input().orElseThrow());
    assertEquals(
        Path.of("Saída VM").toAbsolutePath().normalize(), result.outputDirectory().orElseThrow());
  }

  @Test
  void doesNotMutateCompileArgumentArray() {
    String[] arguments = {"compile", "Main.jack", "--output-dir", "out"};
    String[] original = arguments.clone();

    parser.parse(arguments);

    assertArrayEquals(original, arguments);
  }

  @Test
  void rejectsCompileWithoutInput() {
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> parser.parse(new String[] {"compile"}));

    assertTrue(error.getMessage().contains("Compile requires an input path"));
    assertTrue(error.getMessage().contains("Usage:"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", "--output-dir", "--help"})
  void rejectsBlankOrOptionLikeCompileInput(String input) {
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse(new String[] {"compile", input}));
  }

  @Test
  void rejectsNullCompileInput() {
    assertThrows(
        IllegalArgumentException.class, () -> parser.parse(new String[] {"compile", null}));
  }

  @Test
  void rejectsAdditionalCompilePositionalArgument() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"compile", "Main.jack", "extra"}));

    assertTrue(error.getMessage().contains("Unexpected argument"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"--unknown", "--help", "-v"})
  void rejectsUnknownCompileOptions(String option) {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"compile", "Main.jack", option}));

    assertTrue(error.getMessage().contains("Unknown option"));
  }

  @Test
  void rejectsDuplicateCompileOutputDirectoryOption() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                parser.parse(
                    new String[] {
                      "compile", "Main.jack", "--output-dir", "out", "--output-dir", "other"
                    }));

    assertTrue(error.getMessage().contains("Duplicate"));
  }

  @Test
  void rejectsCompileOutputOptionWithoutDirectory() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"compile", "Main.jack", "--output-dir"}));

    assertTrue(error.getMessage().contains("Missing directory"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", "--help", "--output-dir"})
  void rejectsBlankOrOptionLikeCompileOutputDirectory(String directory) {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(new String[] {"compile", "Main.jack", "--output-dir", directory}));
  }

  @Test
  void rejectsNullCompileOutputDirectory() {
    assertThrows(
        IllegalArgumentException.class,
        () -> parser.parse(new String[] {"compile", "Main.jack", "--output-dir", null}));
  }

  @Test
  void rejectsInvalidCompileInputPath() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"compile", "bad\0path"}));

    assertInstanceOf(InvalidPathException.class, error.getCause());
    assertTrue(error.getMessage().contains("Invalid input path"));
  }

  @Test
  void rejectsInvalidCompileOutputDirectoryPath() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"compile", "Main.jack", "--output-dir", "bad\0path"}));

    assertInstanceOf(InvalidPathException.class, error.getCause());
    assertTrue(error.getMessage().contains("Invalid output directory path"));
  }

  @Test
  void rejectsNullTrailingCompileArgument() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(new String[] {"compile", "Main.jack", null}));

    assertTrue(error.getMessage().contains("Unexpected argument"));
  }
}
