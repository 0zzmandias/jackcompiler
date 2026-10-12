package jackcompiler.lexer;

import java.util.List;
import java.util.Objects;

/** Skips consecutive whitespace and comments between Jack tokens. */
final class TriviaSkipper {

  private static final List<TriviaConsumer> CONSUMERS =
      List.of(new WhitespaceConsumer(), new LineCommentConsumer(), new BlockCommentConsumer());

  /** Advances to the next significant character or the end of the source. */
  void skip(SourceCursor cursor) {
    Objects.requireNonNull(cursor, "cursor");

    boolean consumed;

    do {
      consumed = false;

      for (TriviaConsumer consumer : CONSUMERS) {
        if (consumer.canConsume(cursor)) {
          consumer.consume(cursor);
          consumed = true;
          break;
        }
      }
    } while (consumed);
  }
}
