package jackcompiler.parser;

import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.token.TokenizationResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Navigates one immutable lexical result while parsing a Jack source file. */
final class TokenCursor {

  private final List<Token> tokens;
  private final SourcePosition endPosition;
  private int index;

  TokenCursor(TokenizationResult result) {
    Objects.requireNonNull(result, "result");
    tokens = result.tokens();
    endPosition = result.endPosition();
  }

  /** Returns the current token without consuming it, or empty at EOF. */
  Optional<Token> peek() {
    return peek(0);
  }

  /** Returns a token at a nonnegative lookahead offset, or empty beyond the last token. */
  Optional<Token> peek(int offset) {
    if (offset < 0) {
      throw new IllegalArgumentException("Lookahead offset must not be negative.");
    }

    int remaining = tokens.size() - index;
    return offset < remaining ? Optional.of(tokens.get(index + offset)) : Optional.empty();
  }

  /** Checks the type of the current token without consuming it. */
  boolean match(TokenType type) {
    Objects.requireNonNull(type, "type");
    return peek().filter(token -> token.type() == type).isPresent();
  }

  /** Checks the type and original value of the current token without consuming it. */
  boolean match(TokenType type, String value) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
    return peek().filter(token -> token.type() == type && token.value().equals(value)).isPresent();
  }

  /** Returns and consumes one token, reporting an error if the input has ended. */
  Token advance() {
    Token token =
        peek().orElseThrow(() -> new SyntaxException("token", Optional.empty(), position()));
    index++;
    return token;
  }

  /** Requires and consumes a token of the specified type without advancing on mismatch. */
  Token consume(TokenType type) {
    Objects.requireNonNull(type, "type");
    if (!match(type)) {
      throw new SyntaxException("token of type " + type, peek(), position());
    }
    return advance();
  }

  /** Requires and consumes a token with the specified type and value. */
  Token consume(TokenType type, String value) {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
    if (!match(type, value)) {
      throw new SyntaxException(type + " '" + value + "'", peek(), position());
    }
    return advance();
  }

  /** Indicates whether all lexical tokens have been consumed. */
  boolean isAtEnd() {
    return index == tokens.size();
  }

  /** Returns the next token position, or the original lexical EOF position. */
  SourcePosition position() {
    return isAtEnd() ? endPosition : tokens.get(index).position();
  }
}
