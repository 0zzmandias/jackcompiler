package jackcompiler.compiler;

import jackcompiler.syntax.ParseNode;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import java.util.Objects;
import java.util.Optional;

/** Reports a semantic or concrete-syntax-tree compilation error. */
public final class CompilationException extends RuntimeException {

  private final String description;
  private final Optional<SourcePosition> position;

  /** Creates a compilation error without an available source position. */
  public CompilationException(String description) {
    this(description, Optional.empty(), null);
  }

  /** Creates a compilation error at an original Jack source position. */
  public CompilationException(String description, SourcePosition position) {
    this(description, Optional.of(Objects.requireNonNull(position, "position")), null);
  }

  /** Creates a positioned compilation error retaining its original cause. */
  public CompilationException(String description, SourcePosition position, Throwable cause) {
    this(
        description,
        Optional.of(Objects.requireNonNull(position, "position")),
        Objects.requireNonNull(cause, "cause"));
  }

  /** Creates an error at the first token in a syntax subtree, when one exists. */
  static CompilationException atNode(String description, ParseNode node) {
    return new CompilationException(description, locate(node), null);
  }

  /** Creates a subtree error retaining the cause and any original source position. */
  static CompilationException atNode(String description, ParseNode node, Throwable cause) {
    return new CompilationException(
        description, locate(node), Objects.requireNonNull(cause, "cause"));
  }

  private CompilationException(
      String description, Optional<SourcePosition> position, Throwable cause) {
    super(formatMessage(description, position), cause);
    this.description = description;
    this.position = position;
  }

  /** Returns the diagnostic without its compilation prefix or source location. */
  public String description() {
    return description;
  }

  /** Returns the original position, or empty when no source token was available. */
  public Optional<SourcePosition> position() {
    return position;
  }

  private static Optional<SourcePosition> locate(ParseNode node) {
    return SyntaxTreeNavigator.firstToken(Objects.requireNonNull(node, "node"))
        .map(Token::position);
  }

  private static String formatMessage(String description, Optional<SourcePosition> position) {
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(position, "position");
    if (description.isBlank()) {
      throw new IllegalArgumentException("Compilation error description must not be blank.");
    }

    return position
        .map(
            source ->
                "Compilation error at line "
                    + source.line()
                    + ", column "
                    + source.column()
                    + ": "
                    + description)
        .orElse("Compilation error: " + description);
  }
}
