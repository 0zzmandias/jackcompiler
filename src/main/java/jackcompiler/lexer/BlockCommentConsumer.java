package jackcompiler.lexer;

import jackcompiler.token.SourcePosition;
import java.util.Objects;

/** Consumes non-nested Jack block comments without producing tokens. */
final class BlockCommentConsumer implements TriviaConsumer {

  @Override
  public boolean canConsume(SourceCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");
    return cursor.startsWith("/*");
  }

  @Override
  public void consume(SourceCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");

    SourcePosition start = cursor.position();

    if (!canConsume(cursor)) {
      throw new LexicalException("Expected a Jack block comment.", start);
    }

    cursor.advance();
    cursor.advance();

    while (!cursor.isAtEnd()) {
      if (cursor.startsWith("*/")) {
        cursor.advance();
        cursor.advance();
        return;
      }

      cursor.advance();
    }

    throw new LexicalException("Unterminated Jack block comment.", start);
  }
}
