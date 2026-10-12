package jackcompiler.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Reads Jack source text independently of file discovery and tokenization. */
public final class JackSourceReader {

  private static final String JACK_EXTENSION = ".jack";

  /**
   * Reads a Jack source file using strict UTF-8 decoding.
   *
   * <p>The original text, including line endings, is preserved. Malformed or unmappable UTF-8 input
   * is rejected rather than silently replaced with a substitute character.
   *
   * @param sourceFile path to a Jack source file
   * @return source text decoded as UTF-8
   * @throws IOException if the file cannot be read or decoded
   */
  public String read(Path sourceFile) throws IOException {
    Objects.requireNonNull(sourceFile, "sourceFile");

    Path fileName = sourceFile.getFileName();

    if (fileName == null || !fileName.toString().endsWith(JACK_EXTENSION)) {
      throw new IllegalArgumentException(
          "Source file must have the .jack extension: " + sourceFile);
    }

    return Files.readString(sourceFile, StandardCharsets.UTF_8);
  }
}
