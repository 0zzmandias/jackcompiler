package jackcompiler.application;

import java.nio.file.Path;
import java.util.Objects;

/** Contextualizes a source-processing failure while retaining its original cause. */
public final class SourceProcessingException extends Exception {

  private final Path sourceFile;

  /**
   * Associates a processing failure with the source responsible for it.
   *
   * <p>For failures during input discovery, the source path identifies the requested input because
   * no individual source file has been selected yet.
   *
   * @param sourceFile source being processed, or requested input during discovery
   * @param cause original lexical, serialization, or I/O failure
   */
  public SourceProcessingException(Path sourceFile, Throwable cause) {
    super(message(sourceFile, cause), cause);
    this.sourceFile = sourceFile.toAbsolutePath().normalize();
  }

  /** Returns the normalized source path associated with the failure. */
  public Path sourceFile() {
    return sourceFile;
  }

  private static String message(Path sourceFile, Throwable cause) {
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(cause, "cause");

    return "Failed to process Jack source "
        + sourceFile.toAbsolutePath().normalize()
        + ": "
        + cause.getMessage();
  }
}
