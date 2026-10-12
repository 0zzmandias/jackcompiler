package jackcompiler.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.cli.CliArguments.Command;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CliArgumentsTest {

  @Test
  void representsHelpWithoutSourceOrOutputDirectory() {
    var arguments = new CliArguments(Command.HELP, Optional.empty(), Optional.empty());

    assertEquals(Command.HELP, arguments.command());
    assertTrue(arguments.input().isEmpty());
    assertTrue(arguments.outputDirectory().isEmpty());
  }

  @Test
  void representsTokenizeWithoutCustomOutputDirectory() {
    var arguments =
        new CliArguments(Command.TOKENIZE, Optional.of(Path.of("Main.jack")), Optional.empty());

    assertEquals(
        Path.of("Main.jack").toAbsolutePath().normalize(), arguments.input().orElseThrow());
    assertTrue(arguments.outputDirectory().isEmpty());
  }

  @Test
  void normalizesBothInputAndOutputPaths() {
    var arguments =
        new CliArguments(
            Command.TOKENIZE,
            Optional.of(Path.of("examples", "..", "Main.jack")),
            Optional.of(Path.of("out", "temp", "..")));

    assertEquals(
        Path.of("Main.jack").toAbsolutePath().normalize(), arguments.input().orElseThrow());
    assertEquals(
        Path.of("out").toAbsolutePath().normalize(), arguments.outputDirectory().orElseThrow());
  }

  @Test
  void preservesRecordValueEquality() {
    var a = new CliArguments(Command.TOKENIZE, Optional.of(Path.of("Main.jack")), Optional.empty());
    var b =
        new CliArguments(
            Command.TOKENIZE, Optional.of(Path.of(".", "Main.jack")), Optional.empty());

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void doesNotRequireSourceToExist() {
    var arguments =
        new CliArguments(
            Command.TOKENIZE,
            Optional.of(Path.of("a-file-that-does-not-exist.jack")),
            Optional.empty());

    assertFalse(arguments.input().isEmpty());
  }

  @Test
  void rejectsMissingInputForTokenize() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliArguments(Command.TOKENIZE, Optional.empty(), Optional.empty()));
  }

  @Test
  void rejectsInputWithHelp() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliArguments(Command.HELP, Optional.of(Path.of("Main.jack")), Optional.empty()));
  }

  @Test
  void rejectsOutputDirectoryWithHelp() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CliArguments(Command.HELP, Optional.empty(), Optional.of(Path.of("out"))));
  }

  @Test
  void rejectsNullCommand() {
    assertThrows(
        NullPointerException.class,
        () -> new CliArguments(null, Optional.empty(), Optional.empty()));
  }

  @Test
  void rejectsNullInputOptional() {
    assertThrows(
        NullPointerException.class,
        () -> new CliArguments(Command.TOKENIZE, null, Optional.empty()));
  }

  @Test
  void rejectsNullOutputDirectoryOptional() {
    assertThrows(
        NullPointerException.class,
        () -> new CliArguments(Command.TOKENIZE, Optional.of(Path.of("Main.jack")), null));
  }
}
