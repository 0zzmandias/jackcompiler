package jackcompiler.lexer;

/** Contract for consuming whitespace and comments without producing tokens. */
interface TriviaConsumer {

  /** Checks whether trivia starts at the current position without advancing. */
  boolean canConsume(SourceCursor cursor);

  /** Consumes one contiguous trivia segment. */
  void consume(SourceCursor cursor);
}
