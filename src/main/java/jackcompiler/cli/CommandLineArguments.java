package jackcompiler.cli;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

/** Parsed command-line arguments for the Jack compiler. */
public record CommandLineArguments(Mode mode, Path input) {

  /** Supported compiler operations. */
  public enum Mode {
    TOKENIZE,
    PARSE
  }

  public static final String USAGE = "Usage: jackcompiler <tokenize|parse> <file.jack|directory>";

  /** Validates and normalizes command-line arguments. */
  public CommandLineArguments {
    Objects.requireNonNull(mode, "mode");
    input = Objects.requireNonNull(input, "input").toAbsolutePath().normalize();
  }

  /**
   * Parses a compiler operation and a source path.
   *
   * <p>The parse operation is reserved for the syntax-analysis phase. File existence and extensions
   * are validated by the source-file layer.
   *
   * @param args arguments supplied to the application
   * @return validated command and normalized input path
   * @throws IllegalArgumentException if the arguments are invalid
   */
  public static CommandLineArguments parse(String[] args) {
    Objects.requireNonNull(args, "args");

    if (args.length != 2) {
      throw new IllegalArgumentException(USAGE);
    }

    if (args[0] == null) {
      throw new IllegalArgumentException("Command cannot be null.\n" + USAGE);
    }

    if (args[1] == null || args[1].isBlank()) {
      throw new IllegalArgumentException("Input path cannot be empty.\n" + USAGE);
    }

    Mode mode =
        switch (args[0]) {
          case "tokenize" -> Mode.TOKENIZE;
          case "parse" -> Mode.PARSE;
          default ->
              throw new IllegalArgumentException("Unknown command: " + args[0] + "\n" + USAGE);
        };

    try {
      return new CommandLineArguments(mode, Path.of(args[1]));
    } catch (InvalidPathException exception) {
      throw new IllegalArgumentException("Invalid input path: " + args[1], exception);
    }
  }
}
