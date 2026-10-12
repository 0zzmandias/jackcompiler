package jackcompiler.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Writes UTF-8 text through a temporary file and controlled publication. */
public final class TextFileWriter {

  /**
   * Publishes UTF-8 text according to an explicit collision policy.
   *
   * <p>Creates missing output directories, refuses symbolic links in the destination path, and
   * prevents writes into source-code and fixture directories. The temporary file is written in the
   * destination directory and removed after success or recoverable failure.
   *
   * <p>CREATE_NEW publishes using a hard link to avoid overwriting an existing destination.
   * REPLACE_EXISTING tries an atomic move and falls back to a normal move if atomic moves are not
   * supported.
   *
   * @param destination output file path
   * @param content text to write
   * @param policy policy governing existing files
   * @return absolute, normalized destination
   * @throws IOException if the destination is unsafe or publication fails
   */
  public Path write(Path destination, String content, WritePolicy policy) throws IOException {
    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(policy, "policy");

    Path normalized = destination.toAbsolutePath().normalize();
    Path parent = normalized.getParent();

    if (parent == null || normalized.getFileName() == null) {
      throw new IOException("Expected an output file path: " + normalized);
    }

    rejectProtectedSourcePath(normalized);
    rejectSymbolicLinks(parent);

    Files.createDirectories(parent);

    rejectSymbolicLinks(parent);
    checkDestination(normalized, policy);

    Path temporary = Files.createTempFile(parent, ".jackcompiler-", ".tmp");

    try {
      Files.writeString(
          temporary, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);

      rejectSymbolicLinks(parent);
      checkDestination(normalized, policy);

      if (policy == WritePolicy.CREATE_NEW) {
        // Atomic publication without replacing an existing destination.
        Files.createLink(normalized, temporary);
      } else {
        try {
          Files.move(
              temporary,
              normalized,
              StandardCopyOption.ATOMIC_MOVE,
              StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
          Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
        }
      }

      return normalized;
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void checkDestination(Path destination, WritePolicy policy) throws IOException {
    if (Files.isSymbolicLink(destination)) {
      throw new IOException("Symbolic-link output destination is not permitted: " + destination);
    }

    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      if (policy == WritePolicy.CREATE_NEW) {
        throw new FileAlreadyExistsException(destination.toString());
      }

      if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("Existing output is not a regular file: " + destination);
      }
    }
  }

  private static void rejectSymbolicLinks(Path directory) throws IOException {
    Path current = directory.getRoot();

    for (Path part : directory) {
      current = current.resolve(part);

      if (Files.isSymbolicLink(current)) {
        throw new IOException("Symbolic-link output directory is not permitted: " + current);
      }
    }
  }

  private static void rejectProtectedSourcePath(Path destination) throws IOException {
    String filename = destination.getFileName().toString();

    if (filename.endsWith(".jack")) {
      throw new IOException("Cannot overwrite Jack source files: " + destination);
    }

    String previous2 = "";
    String previous1 = "";

    for (Path part : destination) {
      String element = part.toString();

      if (("src".equals(previous2)
              && "test".equals(previous1)
              && ("resources".equals(element) || "java".equals(element)))
          || ("src".equals(previous2) && "main".equals(previous1) && "java".equals(element))) {
        throw new IOException("Cannot write into source or reference directories: " + destination);
      }

      previous2 = previous1;
      previous1 = element;
    }
  }
}
