package jackcompiler.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Locates Jack source files without reading their contents. */
public final class JackFileResolver {

  private static final String JACK_EXTENSION = ".jack";

  /**
   * Resolves a single Jack file or all Jack files directly inside a directory.
   *
   * <p>Directory traversal is not recursive. Files are selected using the case-sensitive .jack
   * extension and sorted by file name.
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
                .filter(JackFileResolver::isJackFile)
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

  private static boolean isJackFile(Path path) {
    Path fileName = path.getFileName();
    return fileName != null && fileName.toString().endsWith(JACK_EXTENSION);
  }
}
