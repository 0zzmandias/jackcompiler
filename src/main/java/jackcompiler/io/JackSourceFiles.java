package jackcompiler.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Locates and reads Jack source files using UTF-8. */
public final class JackSourceFiles {

  private static final String JACK_EXTENSION = ".jack";

  /**
   * Resolves a single Jack file or all Jack files directly inside a directory.
   *
   * @param input source file or directory
   * @return immutable list of absolute, normalized source paths
   * @throws IOException if the input cannot be located or no Jack files are found
   */
  public List<Path> resolve(Path input) throws IOException {
    Objects.requireNonNull(input, "input");

    Path normalized = input.toAbsolutePath().normalize();

    if (!Files.exists(normalized)) {
      throw new NoSuchFileException(normalized.toString());
    }

    if (Files.isRegularFile(normalized)) {
      if (!isJackFile(normalized)) {
        throw new IllegalArgumentException(
            "Source file must have the .jack extension: " + normalized);
      }

      return List.of(normalized);
    }

    if (Files.isDirectory(normalized)) {
      try (Stream<Path> entries = Files.list(normalized)) {
        List<Path> sources =
            entries
                .filter(Files::isRegularFile)
                .filter(JackSourceFiles::isJackFile)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();

        if (sources.isEmpty()) {
          throw new IOException("No .jack files found in directory: " + normalized);
        }

        return sources;
      }
    }

    throw new IOException("Input is not a regular file or directory: " + normalized);
  }

  /**
   * Reads a Jack source file without changing its text or line endings.
   *
   * @param sourceFile path to a Jack source file
   * @return source text decoded as UTF-8
   * @throws IOException if the file cannot be read or decoded
   */
  public String read(Path sourceFile) throws IOException {
    Objects.requireNonNull(sourceFile, "sourceFile");

    if (!isJackFile(sourceFile)) {
      throw new IllegalArgumentException(
          "Source file must have the .jack extension: " + sourceFile);
    }

    return Files.readString(sourceFile, StandardCharsets.UTF_8);
  }

  private static boolean isJackFile(Path path) {
    Path fileName = path.getFileName();
    return fileName != null && fileName.toString().endsWith(JACK_EXTENSION);
  }
}
