package jackcompiler.syntax;

import jackcompiler.token.Token;
import java.util.Objects;

/**
 * Immutable leaf of a concrete syntax tree, retaining its original lexical token.
 *
 * @param token original token, including its type, value and source position
 */
public record TerminalNode(Token token) implements ParseNode {

  public TerminalNode {
    Objects.requireNonNull(token, "token");
  }
}
