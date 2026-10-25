package jackcompiler.parser;

import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import java.util.Objects;
import java.util.Optional;

/** Reports a Jack grammar violation at an unexpected token or at end of input. */
public final class SyntaxException extends RuntimeException {

  private final String expected;
  private final Optional<Token> found;
  private final SourcePosition position;

  /**
   * Creates a syntax error without coupling it to a source file or command-line interface.
   *
   * @param expected nonblank description of the expected grammatical element
   * @param found unexpected token, or empty if the input has ended
   * @param position position of the unexpected token, or the end-of-input position
   * @throws NullPointerException if any argument is null
   * @throws IllegalArgumentException if the expectation is blank or a token's position differs from
   *     the supplied position
   */
  public SyntaxException(String expected, Optional<Token> found, SourcePosition position) {
    super(formatMessage(expected, found));
    this.expected = expected;
    this.found = found;
    this.position = Objects.requireNonNull(position, "position");

    found.ifPresent(
        token -> {
          if (!token.position().equals(position)) {
            throw new IllegalArgumentException("Unexpected token and error positions must match.");
          }
        });
  }

  /** Returns a human-readable description of the expected grammatical element. */
  public String expected() {
    return expected;
  }

  /** Returns the unexpected token, or empty if the parser reached end of input. */
  public Optional<Token> found() {
    return found;
  }

  /** Returns the source position of the unexpected token or end of input. */
  public SourcePosition position() {
    return position;
  }

  private static String formatMessage(String expected, Optional<Token> found) {
    Objects.requireNonNull(expected, "expected");
    Objects.requireNonNull(found, "found");
    if (expected.isBlank()) {
      throw new IllegalArgumentException("Expected element description must not be blank.");
    }

    String actual =
        found.map(token -> token.type() + " '" + token.value() + "'").orElse("end of input (EOF)");
    return "Expected " + expected + ", but found " + actual + ".";
  }
}
