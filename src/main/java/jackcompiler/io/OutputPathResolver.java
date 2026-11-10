package jackcompiler.io;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Resolves output paths without reading or writing source files. */
public final class OutputPathResolver {

  private static final String JACK_EXTENSION = ".jack";
  private static final Path DEFAULT_TOKEN_OUTPUT_DIRECTORY = Path.of("target", "generated-tokens");
  private static final Path DEFAULT_SYNTAX_OUTPUT_DIRECTORY = Path.of("target", "generated-parser");

  /**
   * Resolves the destination of generated output for a Jack source.
   *
   * <p>Token XML and syntax XML retain their existing default directories. VM output defaults to
   * the source directory. A custom directory is used directly for individual sources; for recursive
   * VM compilation, use {@link #resolveVm(Path, Path, Optional)} instead. No files or directories
   * are created by resolution.
   *
   * @param source Jack source file
   * @param outputDirectory optional output directory
   * @param kind output kind
   * @return absolute, normalized output path
   * @throws IOException if the destination is unsafe or aliases a source
   */
  public Path resolve(Path source, Optional<Path> outputDirectory, OutputKind kind)
      throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(outputDirectory, "outputDirectory");
    Objects.requireNonNull(kind, "kind");

    if (kind == OutputKind.VM) {
      return resolveVm(source, source, outputDirectory);
    }

    Path sourcePath = source.toAbsolutePath().normalize();
    String baseName = baseName(sourcePath);

    Path defaultDirectory =
        switch (kind) {
          case TOKEN_XML -> DEFAULT_TOKEN_OUTPUT_DIRECTORY;
          case SYNTAX_XML -> DEFAULT_SYNTAX_OUTPUT_DIRECTORY;
          case VM -> throw new IllegalStateException("VM is resolved separately.");
        };

    Path directory = outputDirectory.orElse(defaultDirectory).toAbsolutePath().normalize();
    String suffix =
        switch (kind) {
          case TOKEN_XML -> "T.xml";
          case SYNTAX_XML -> "P.xml";
          case VM -> throw new IllegalStateException("VM is resolved separately.");
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

  /**
   * Resolves one VM output relative to the original file or directory input.
   *
   * <p>Without an explicit destination, VM goes beside its Jack source. With an explicit
   * destination, source subdirectories are mirrored relative to a directory input. A single source
   * file is written directly under the selected output directory.
   *
   * @param source selected Jack source
   * @param input original file or directory supplied for compilation
   * @param outputDirectory optional custom destination root
   * @return safe absolute, normalized VM destination
   * @throws IOException if the destination is unsafe or occupied under CREATE_NEW
   */
  public Path resolveVm(Path source, Path input, Optional<Path> outputDirectory)
      throws IOException {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(outputDirectory, "outputDirectory");

    Path sourcePath = source.toAbsolutePath().normalize();
    Path inputPath = input.toAbsolutePath().normalize();
    String vmName = baseName(sourcePath) + ".vm";

    Path relative;
    if (sourcePath.equals(inputPath)) {
      relative = sourcePath.getFileName();
    } else if (Files.isDirectory(inputPath, LinkOption.NOFOLLOW_LINKS)
        && sourcePath.startsWith(inputPath)) {
      relative = inputPath.relativize(sourcePath);
    } else {
      throw new IllegalArgumentException(
          "Jack source is not contained in the compilation input: " + sourcePath);
    }

    Path destination =
        outputDirectory
            .map(path -> path.toAbsolutePath().normalize().resolve(relative).resolveSibling(vmName))
            .orElseGet(() -> sourcePath.resolveSibling(vmName));

    validateVmDestination(destination, writePolicy(outputDirectory));
    rejectSourceAlias(destination, sourcePath);
    return destination;
  }

  /**
   * Resolves an entire compilation batch before any source is compiled or output is written.
   *
   * <p>Results follow the supplied source order and are immutable. Rejects duplicate output names
   * and destinations that alias any source in the batch. The writer must revalidate destinations at
   * publication time because the filesystem may change after resolution.
   *
   * @param input original compilation input
   * @param sources discovered Jack source files, in compilation order
   * @param outputDirectory optional custom destination root
   * @return immutable source-to-destination mapping preserving input order
   * @throws IOException if any destination is unsafe or collides
   */
  public Map<Path, Path> resolveVmBatch(
      Path input, List<Path> sources, Optional<Path> outputDirectory) throws IOException {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(sources, "sources");
    Objects.requireNonNull(outputDirectory, "outputDirectory");
    if (sources.isEmpty()) {
      throw new IOException("No Jack sources supplied for VM output resolution.");
    }

    Map<Path, Path> resolved = new LinkedHashMap<>();
    for (Path source : sources) {
      Path sourcePath = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
      Path destination = resolveVm(sourcePath, input, outputDirectory);
      if (resolved.containsKey(sourcePath) || resolved.containsValue(destination)) {
        throw new IOException("Duplicate VM output destination: " + destination);
      }
      resolved.put(sourcePath, destination);
    }

    for (Path destination : resolved.values()) {
      for (Path source : resolved.keySet()) {
        rejectSourceAlias(destination, source);
      }
    }
    return Collections.unmodifiableMap(resolved);
  }

  /** Determines the existing publication policy without creating output files. */
  public WritePolicy writePolicy(Optional<Path> outputDirectory) {
    Objects.requireNonNull(outputDirectory, "outputDirectory");
    return outputDirectory.isPresent() ? WritePolicy.CREATE_NEW : WritePolicy.REPLACE_EXISTING;
  }

  private static String baseName(Path source) {
    Path fileName = source.getFileName();
    if (fileName == null) {
      throw new IllegalArgumentException("Expected a Jack source file path.");
    }
    String name = fileName.toString();
    if (!name.endsWith(JACK_EXTENSION) || name.length() == JACK_EXTENSION.length()) {
      throw new IllegalArgumentException(
          "Source file must have a nonempty name and the .jack extension: " + source);
    }
    return name.substring(0, name.length() - JACK_EXTENSION.length());
  }

  private static void validateVmDestination(Path destination, WritePolicy policy)
      throws IOException {
    rejectProtectedDirectories(destination);
    for (Path parent = destination.getParent(); parent != null; parent = parent.getParent()) {
      if (Files.isSymbolicLink(parent)) {
        throw new IOException("Symbolic-link output directory is not permitted: " + parent);
      }
      if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)
          && !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("Output parent is not a directory: " + parent);
      }
    }
    if (Files.isSymbolicLink(destination)) {
      throw new IOException("Symbolic-link output destination is not permitted: " + destination);
    }
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("Existing VM output is not a regular file: " + destination);
      }
      if (policy == WritePolicy.CREATE_NEW) {
        throw new FileAlreadyExistsException(destination.toString());
      }
    }
  }

  private static void rejectProtectedDirectories(Path destination) throws IOException {
    String secondPrevious = "";
    String previous = "";
    for (Path part : destination) {
      String element = part.toString();
      if (("src".equals(secondPrevious)
              && "test".equals(previous)
              && ("resources".equals(element) || "java".equals(element)))
          || ("src".equals(secondPrevious) && "main".equals(previous) && "java".equals(element))) {
        throw new IOException("Cannot write into source or reference directories: " + destination);
      }
      secondPrevious = previous;
      previous = element;
    }
  }

  private static void rejectSourceAlias(Path destination, Path source) throws IOException {
    if (destination.equals(source)
        || (Files.exists(source)
            && Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
            && Files.isSameFile(destination, source))) {
      throw new IOException("Output destination aliases a Jack source: " + destination);
    }
  }
}
