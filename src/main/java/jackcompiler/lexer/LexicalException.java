package jackcompiler.lexer;

import jackcompiler.token.SourcePosition;
import java.util.Objects;

/** Indicates an invalid lexical element at a specific source position. */
public final class LexicalException extends RuntimeException {

  private final SourcePosition position;

  /**
   * Creates a lexical error.
   *
   * @param message description of the lexical error
   * @param position position where the error was detected
   */
  public LexicalException(String message, SourcePosition position) {
    super(Objects.requireNonNull(message, "message"));
    this.position = Objects.requireNonNull(position, "position");
  }

  /** Returns the source position where the error was detected. */
  public SourcePosition position() {
    return position;
  }
}
