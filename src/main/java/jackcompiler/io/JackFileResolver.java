package jackcompiler.io;

import java.io.IOException;
import java.io.UncheckedIOException;
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

  /**
   * Resolves a Jack file or discovers all Jack files recursively under a directory.
   *
   * <p>This explicit operation is intended for compilation. Unlike {@link #resolve(Path)}, it
   * visits nested directories. Directory symbolic links are never traversed, including when the
   * requested root itself is a symbolic link to a directory. Regular-file symbolic links retain the
   * same source-file behavior as the non-recursive resolver. Results are ordered by their paths
   * relative to the requested directory, not merely by their file names.
   *
   * @param input source file or directory to search recursively
   * @return immutable list of absolute, normalized Jack source paths
   * @throws IOException if the input is missing, cannot be traversed or contains no Jack files
   */
  public List<Path> resolveRecursively(Path input) throws IOException {
    Objects.requireNonNull(input, "input");
    Path normalized = input.toAbsolutePath().normalize();

    if (!Files.exists(normalized)) {
      throw new NoSuchFileException(normalized.toString());
    }

    if (Files.isRegularFile(normalized)) {
      return resolve(normalized);
    }

    if (Files.isDirectory(normalized)) {
      if (Files.isSymbolicLink(normalized)) {
        throw new IOException("Cannot recursively traverse a symbolic directory: " + normalized);
      }

      try (Stream<Path> entries = Files.walk(normalized)) {
        List<Path> sources =
            entries
                .filter(Files::isRegularFile)
                .filter(JackFileResolver::isJackFile)
                .sorted(Comparator.comparing(path -> normalized.relativize(path).toString()))
                .toList();

        if (sources.isEmpty()) {
          throw new IOException("No .jack files found recursively in directory: " + normalized);
        }

        return sources;
      } catch (UncheckedIOException cause) {
        throw cause.getCause();
      }
    }

    throw new IOException("Input is not a regular file or directory: " + normalized);
  }

  private static boolean isJackFile(Path path) {
    Path fileName = path.getFileName();
    return fileName != null && fileName.toString().endsWith(JACK_EXTENSION);
  }
}
