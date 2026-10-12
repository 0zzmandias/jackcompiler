package jackcompiler.lexer;

import java.util.Objects;

/** Consumes Jack line comments starting with two slashes. */
final class LineCommentConsumer implements TriviaConsumer {

  @Override
  public boolean canConsume(SourceCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");
    return cursor.startsWith("//");
  }

  @Override
  public void consume(SourceCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");

    if (!canConsume(cursor)) {
      throw new LexicalException("Expected a Jack line comment.", cursor.position());
    }

    cursor.advance();
    cursor.advance();

    while (!cursor.isAtEnd() && cursor.peek() != '\n') {
      cursor.advance();
    }
  }
}
