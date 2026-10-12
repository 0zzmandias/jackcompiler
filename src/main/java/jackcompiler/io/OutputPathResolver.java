package jackcompiler.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Resolves output paths without reading or writing source files. */
public final class OutputPathResolver {

  private static final String JACK_EXTENSION = ".jack";
  private static final Path DEFAULT_OUTPUT_DIRECTORY = Path.of("target", "generated-tokens");

  /**
   * Resolves the destination of generated output for a Jack source.
   *
   * <p>If no directory is supplied, the output goes into target/generated-tokens relative to the
   * working directory. No directories or files are created here.
   *
   * @param source Jack source file
   * @param outputDirectory optional user-selected directory
   * @param kind output kind
   * @return absolute, normalized output file path
   * @throws IOException if a pre-existing destination aliases the source
   */
  public Path resolve(Path source, Optional<Path> outputDirectory, OutputKind kind)
      throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(outputDirectory, "outputDirectory");
    Objects.requireNonNull(kind, "kind");

    Path sourcePath = source.toAbsolutePath().normalize();
    Path fileName = sourcePath.getFileName();

    if (fileName == null) {
      throw new IllegalArgumentException("Expected a Jack source file path.");
    }

    String name = fileName.toString();

    if (!name.endsWith(JACK_EXTENSION) || name.length() == JACK_EXTENSION.length()) {
      throw new IllegalArgumentException(
          "Source file must have a nonempty name and the .jack extension: " + sourcePath);
    }

    Path directory = outputDirectory.orElse(DEFAULT_OUTPUT_DIRECTORY).toAbsolutePath().normalize();

    String baseName = name.substring(0, name.length() - JACK_EXTENSION.length());

    String suffix =
        switch (kind) {
          case TOKEN_XML -> "T.xml";
        };

    Path destination = directory.resolve(baseName + suffix);

    if (sourcePath.equals(destination)
        || (Files.exists(sourcePath)
            && Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
            && Files.isSameFile(sourcePath, destination))) {
      throw new IOException("Output destination aliases the source: " + destination);
    }

    return destination;
  }
}
