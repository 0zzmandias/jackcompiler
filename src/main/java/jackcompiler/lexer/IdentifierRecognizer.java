package jackcompiler.lexer;

import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import java.util.Objects;

/** Recognizes Jack identifiers and distinguishes reserved keywords. */
final class IdentifierRecognizer implements TokenRecognizer {

  @Override
  public boolean canRecognize(SourceCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");
    return JackLexicalRules.isIdentifierStart(cursor.peek());
  }

  @Override
  public Token recognize(SourceCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");

    SourcePosition start = cursor.position();

    if (!canRecognize(cursor)) {
      throw new LexicalException("Expected a Jack identifier or keyword.", start);
    }

    StringBuilder value = new StringBuilder();

    while (JackLexicalRules.isIdentifierPart(cursor.peek())) {
      value.appendCodePoint(cursor.advance());
    }

    String lexeme = value.toString();
    TokenType type = JackLexicalRules.isKeyword(lexeme) ? TokenType.KEYWORD : TokenType.IDENTIFIER;

    return new Token(type, lexeme, start);
  }
}
