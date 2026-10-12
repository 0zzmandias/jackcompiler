package jackcompiler.cli;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Immutable, validated arguments for an implemented JackCompiler operation. */
public record CliArguments(Command command, Optional<Path> input, Optional<Path> outputDirectory) {

  /** Operations supported during the lexical-analysis phase. */
  public enum Command {
    TOKENIZE,
    HELP
  }

  /** Validates argument combinations and normalizes paths without accessing the file system. */
  public CliArguments {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(outputDirectory, "outputDirectory");

    if (command == Command.HELP) {
      if (input.isPresent() || outputDirectory.isPresent()) {
        throw new IllegalArgumentException("Help does not accept source or output paths.");
      }
    } else if (input.isEmpty()) {
      throw new IllegalArgumentException("Tokenize requires an input path.");
    }

    input = input.map(path -> path.toAbsolutePath().normalize());
    outputDirectory = outputDirectory.map(path -> path.toAbsolutePath().normalize());
  }
}
