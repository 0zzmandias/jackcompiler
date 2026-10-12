package jackcompiler.cli;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Parses the documented lexical-analysis command line without accessing files. */
public final class CliArgumentParser {

  public static final String USAGE =
      "Usage: jackcompiler tokenize <file.jack|directory> [--output-dir <directory>]\n"
          + "       jackcompiler --help";

  /**
   * Accepts only the implemented tokenize operation and the standalone help option.
   *
   * @param args command-line arguments, excluding the executable name
   * @return validated, normalized arguments
   * @throws IllegalArgumentException if the command line is invalid
   */
  public CliArguments parse(String[] args) {
    Objects.requireNonNull(args, "args");

    if (args.length == 1 && "--help".equals(args[0])) {
      return new CliArguments(CliArguments.Command.HELP, Optional.empty(), Optional.empty());
    }

    if (args.length == 0) {
      throw invalid("Missing command.");
    }
    if (args[0] == null) {
      throw invalid("Command cannot be null.");
    }
    if (!"tokenize".equals(args[0])) {
      throw invalid("Unknown command: " + args[0]);
    }
    if (args.length == 1 || args[1] == null || args[1].isBlank() || args[1].startsWith("--")) {
      throw invalid("Tokenize requires an input path.");
    }

    Path input = parsePath(args[1], "input");
    Optional<Path> outputDirectory = Optional.empty();

    for (int index = 2; index < args.length; index++) {
      String option = args[index];

      if ("--output-dir".equals(option)) {
        if (outputDirectory.isPresent()) {
          throw invalid("Duplicate --output-dir option.");
        }
        if (index + 1 >= args.length
            || args[index + 1] == null
            || args[index + 1].isBlank()
            || args[index + 1].startsWith("--")) {
          throw invalid("Missing directory after --output-dir.");
        }
        outputDirectory = Optional.of(parsePath(args[++index], "output directory"));
      } else if (option != null && option.startsWith("-")) {
        throw invalid("Unknown option: " + option);
      } else {
        throw invalid("Unexpected argument: " + option);
      }
    }

    return new CliArguments(CliArguments.Command.TOKENIZE, Optional.of(input), outputDirectory);
  }

  private static Path parsePath(String raw, String role) {
    try {
      return Path.of(raw);
    } catch (InvalidPathException exception) {
      throw new IllegalArgumentException(
          "Invalid " + role + " path: " + raw + "\n" + USAGE, exception);
    }
  }

  private static IllegalArgumentException invalid(String message) {
    return new IllegalArgumentException(message + "\n" + USAGE);
  }
}
