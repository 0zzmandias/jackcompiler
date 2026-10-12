package jackcompiler.token;

import java.util.Objects;

/**
 * Immutable lexical token.
 *
 * @param type token category
 * @param value original significant content, without string delimiters
 * @param position position where the token begins
 */
public record Token(TokenType type, String value, SourcePosition position) {

  public Token {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(position, "position");
  }
}
