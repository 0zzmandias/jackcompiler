package jackcompiler.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
  @ValueSource(strings = {"parse", "compile", "tokens", "Tokenize", "", "help"})
  void rejectsUnimplementedOrUnknownCommands(String command) {
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
  void includesBothSupportedFormsInUsage() {
    assertTrue(CliArgumentParser.USAGE.contains("tokenize"));
    assertTrue(CliArgumentParser.USAGE.contains("--help"));
    assertTrue(CliArgumentParser.USAGE.contains("--output-dir"));
    assertFalse(CliArgumentParser.USAGE.contains("parse"));
  }
}
