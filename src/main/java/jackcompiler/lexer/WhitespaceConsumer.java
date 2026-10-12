package jackcompiler.lexer;

import java.util.Objects;

/** Consumes contiguous Jack whitespace without producing tokens. */
final class WhitespaceConsumer implements TriviaConsumer {

  @Override
  public boolean canConsume(SourceCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");
    return JackLexicalRules.isWhitespace(cursor.peek());
  }

  @Override
  public void consume(SourceCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");

    if (!canConsume(cursor)) {
      throw new LexicalException("Expected Jack whitespace.", cursor.position());
    }

    while (JackLexicalRules.isWhitespace(cursor.peek())) {
      cursor.advance();
    }
  }
}
