package jackcompiler.lexer;

import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import java.util.Objects;

/** Recognizes Jack integer constants in the inclusive range 0 to 32767. */
final class IntegerRecognizer implements TokenRecognizer {

  @Override
  public boolean canRecognize(SourceCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");
    return JackLexicalRules.isDecimalDigit(cursor.peek());
  }

  @Override
  public Token recognize(SourceCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");

    SourcePosition start = cursor.position();

    if (!canRecognize(cursor)) {
      throw new LexicalException("Expected a Jack integer constant.", start);
    }

    StringBuilder lexeme = new StringBuilder();
    int value = 0;

    while (JackLexicalRules.isDecimalDigit(cursor.peek())) {
      int digit = cursor.peek() - '0';

      if (value > (JackLexicalRules.MAX_INTEGER_CONSTANT - digit) / 10) {
        throw new LexicalException(
            "Integer constant exceeds the Jack limit of "
                + JackLexicalRules.MAX_INTEGER_CONSTANT
                + ".",
            start);
      }

      value = value * 10 + digit;
      lexeme.appendCodePoint(cursor.advance());
    }

    return new Token(TokenType.INTEGER_CONSTANT, lexeme.toString(), start);
  }
}
