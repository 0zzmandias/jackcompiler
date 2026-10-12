package jackcompiler.lexer;

import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import java.util.Objects;

/** Recognizes Jack string constants enclosed in double quotation marks. */
final class StringRecognizer implements TokenRecognizer {

  @Override
  public boolean canRecognize(SourceCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");
    return cursor.peek() == '"';
  }

  @Override
  public Token recognize(SourceCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");

    SourcePosition start = cursor.position();

    if (!canRecognize(cursor)) {
      throw new LexicalException("Expected a Jack string constant.", start);
    }

    cursor.advance();
    StringBuilder content = new StringBuilder();

    while (true) {
      int codePoint = cursor.peek();

      if (codePoint == SourceCursor.EOF) {
        throw new LexicalException("Unterminated Jack string constant.", start);
      }

      if (codePoint == '\n') {
        throw new LexicalException(
            "Line breaks are not allowed inside Jack string constants.", cursor.position());
      }

      if (codePoint == '"') {
        cursor.advance();
        return new Token(TokenType.STRING_CONSTANT, content.toString(), start);
      }

      content.appendCodePoint(cursor.advance());
    }
  }
}
