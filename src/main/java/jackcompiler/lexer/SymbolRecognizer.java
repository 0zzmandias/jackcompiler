package jackcompiler.lexer;

import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import java.util.Objects;

/** Recognizes the nineteen single-character symbols of the Jack language. */
final class SymbolRecognizer implements TokenRecognizer {

  @Override
  public boolean canRecognize(SourceCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");
    return JackLexicalRules.isSymbol(cursor.peek());
  }

  @Override
  public Token recognize(SourceCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");

    SourcePosition start = cursor.position();

    if (!canRecognize(cursor)) {
      throw new LexicalException("Expected a Jack symbol.", start);
    }

    String value = Character.toString(cursor.advance());
    return new Token(TokenType.SYMBOL, value, start);
  }
}
