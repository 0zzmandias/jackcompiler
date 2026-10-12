package jackcompiler.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.cli.CommandLineArguments.Mode;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommandLineArgumentsTest {

  @TempDir Path tempDir;

  @Test
  void parsesTokenizeCommandWithJackFile() {
    CommandLineArguments arguments =
        CommandLineArguments.parse(new String[] {"tokenize", "Main.jack"});

    assertEquals(Mode.TOKENIZE, arguments.mode());
    assertEquals(Path.of("Main.jack").toAbsolutePath().normalize(), arguments.input());
  }

  @Test
  void parsesTokenizeCommandWithDirectory() {
    CommandLineArguments arguments =
        CommandLineArguments.parse(new String[] {"tokenize", "examples"});

    assertEquals(Mode.TOKENIZE, arguments.mode());
    assertEquals(Path.of("examples").toAbsolutePath().normalize(), arguments.input());
  }

  @Test
  void recognizesParseCommandReservedForSecondPhase() {
    CommandLineArguments arguments =
        CommandLineArguments.parse(new String[] {"parse", "Main.jack"});

    assertEquals(Mode.PARSE, arguments.mode());
    assertEquals(Path.of("Main.jack").toAbsolutePath().normalize(), arguments.input());
  }

  @Test
  void normalizesRelativePathWithParentSegments() {
    String input = "folder/../Main.jack";

    CommandLineArguments arguments = CommandLineArguments.parse(new String[] {"tokenize", input});

    assertEquals(Path.of("Main.jack").toAbsolutePath().normalize(), arguments.input());
  }

  @Test
  void preservesSpacesAndUnicodeInFilePaths() {
    String input = "Meus Arquivos/Olá Mundo.jack";

    CommandLineArguments arguments = CommandLineArguments.parse(new String[] {"tokenize", input});

    assertEquals(Path.of(input).toAbsolutePath().normalize(), arguments.input());
  }

  @Test
  void doesNotRequireInputFileToExistDuringParsing() {
    Path missing = tempDir.resolve("Missing.jack");

    assertFalse(Files.exists(missing));

    CommandLineArguments arguments =
        CommandLineArguments.parse(new String[] {"tokenize", missing.toString()});

    assertEquals(Mode.TOKENIZE, arguments.mode());
    assertEquals(missing, arguments.input());
  }

  @Test
  void doesNotModifyProvidedArgumentArray() {
    String[] input = {"tokenize", "Main.jack"};
    String[] original = input.clone();

    CommandLineArguments.parse(input);

    assertArrayEquals(original, input);
  }

  @Test
  void normalizesPathPassedDirectlyToConstructor() {
    CommandLineArguments arguments =
        new CommandLineArguments(Mode.TOKENIZE, Path.of("examples", "..", "Main.jack"));

    assertEquals(Path.of("Main.jack").toAbsolutePath().normalize(), arguments.input());
  }

  @Test
  void equivalentArgumentsHaveRecordValueEquality() {
    CommandLineArguments first = CommandLineArguments.parse(new String[] {"tokenize", "Main.jack"});

    CommandLineArguments second =
        new CommandLineArguments(Mode.TOKENIZE, Path.of(".", "Main.jack"));

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }

  @Test
  void rejectsEmptyArgumentList() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class, () -> CommandLineArguments.parse(new String[] {}));

    assertTrue(error.getMessage().contains("Usage:"));
  }

  @Test
  void rejectsMissingInputArgument() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> CommandLineArguments.parse(new String[] {"tokenize"}));

    assertTrue(error.getMessage().contains("Usage:"));
  }

  @Test
  void rejectsAdditionalArguments() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> CommandLineArguments.parse(new String[] {"tokenize", "Main.jack", "extra"}));

    assertTrue(error.getMessage().contains("Usage:"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"compile", "tokens", "Tokenize", "PARSE", "", "--help"})
  void rejectsUnknownCommands(String command) {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> CommandLineArguments.parse(new String[] {command, "Main.jack"}));

    assertTrue(error.getMessage().contains("Unknown command"));
    assertTrue(error.getMessage().contains("Usage:"));
  }

  @Test
  void rejectsNullArgumentArray() {
    assertThrows(NullPointerException.class, () -> CommandLineArguments.parse(null));
  }

  @Test
  void rejectsNullCommandArgument() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> CommandLineArguments.parse(new String[] {null, "Main.jack"}));

    assertTrue(error.getMessage().contains("Command cannot be null"));
  }

  @Test
  void rejectsNullInputArgument() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> CommandLineArguments.parse(new String[] {"tokenize", null}));

    assertTrue(error.getMessage().contains("Input path cannot be empty"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void rejectsBlankInputPaths(String input) {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> CommandLineArguments.parse(new String[] {"tokenize", input}));

    assertTrue(error.getMessage().contains("Input path cannot be empty"));
  }

  @Test
  void wrapsInvalidFileSystemPaths() {
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> CommandLineArguments.parse(new String[] {"tokenize", "bad\0path"}));

    assertTrue(error.getMessage().contains("Invalid input path"));
    assertInstanceOf(InvalidPathException.class, error.getCause());
  }

  @Test
  void rejectsNullModeInDirectConstructor() {
    assertThrows(
        NullPointerException.class, () -> new CommandLineArguments(null, Path.of("Main.jack")));
  }

  @Test
  void rejectsNullPathInDirectConstructor() {
    assertThrows(NullPointerException.class, () -> new CommandLineArguments(Mode.TOKENIZE, null));
  }

  @Test
  void exposesUsageForApplicationErrorMessages() {
    assertNotNull(CommandLineArguments.USAGE);
    assertTrue(CommandLineArguments.USAGE.contains("tokenize"));
    assertTrue(CommandLineArguments.USAGE.contains("parse"));
    assertTrue(CommandLineArguments.USAGE.contains("file.jack"));
  }
}
