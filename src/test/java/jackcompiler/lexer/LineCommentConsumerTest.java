package jackcompiler.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LineCommentConsumerTest {

  private final LineCommentConsumer consumer = new LineCommentConsumer();

  @ParameterizedTest
  @ValueSource(strings = {"//", "//text", "/// docs", "// 😀"})
  void recognizesLineCommentPrefixesWithoutAdvancing(String input) {
    SourceCursor cursor = new SourceCursor(input);

    assertTrue(consumer.canConsume(cursor));
    assertTrue(consumer.canConsume(cursor));
    assertEquals('/', cursor.peek());
    assertEquals(new SourcePosition(1, 1), cursor.position());
  }

  @ParameterizedTest
  @ValueSource(strings = {"//", "// hello", "/// docs", "// /* block */", "// 😀"})
  void consumesLineCommentsUntilEndOfFile(String input) {
    SourceCursor cursor = new SourceCursor(input);

    consumer.consume(cursor);

    assertTrue(cursor.isAtEnd());
    assertEquals(SourceCursor.EOF, cursor.peek());
    assertEquals(
        new SourcePosition(1, input.codePointCount(0, input.length()) + 1), cursor.position());
  }

  @ParameterizedTest
  @ValueSource(strings = {"//note\nx", "//note\rx", "//note\r\nx"})
  void leavesLineTerminatorsUnconsumed(String input) {
    SourceCursor cursor = new SourceCursor(input);

    consumer.consume(cursor);

    assertEquals('\n', cursor.peek());
    assertEquals(new SourcePosition(1, 7), cursor.position());

    cursor.advance();

    assertEquals('x', cursor.peek());
    assertEquals(new SourcePosition(2, 1), cursor.position());
  }

  @Test
  void handlesEmptyCommentBeforeLineBreak() {
    SourceCursor cursor = new SourceCursor("//\r\n");

    consumer.consume(cursor);

    assertEquals('\n', cursor.peek());
    assertEquals(new SourcePosition(1, 3), cursor.position());

    new WhitespaceConsumer().consume(cursor);

    assertTrue(cursor.isAtEnd());
    assertEquals(new SourcePosition(2, 1), cursor.position());
  }

  @Test
  void allowsWhitespaceConsumerAndIdentifierRecognizerToContinue() {
    SourceCursor cursor = new SourceCursor("//comment\r\nclass");

    consumer.consume(cursor);
    assertEquals('\n', cursor.peek());

    new WhitespaceConsumer().consume(cursor);

    Token token = new IdentifierRecognizer().recognize(cursor);

    assertEquals(TokenType.KEYWORD, token.type());
    assertEquals("class", token.value());
    assertEquals(new SourcePosition(2, 1), token.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void handlesConsecutiveCommentLines() {
    SourceCursor cursor = new SourceCursor("//first\r\n\n//second");

    consumer.consume(cursor);
    new WhitespaceConsumer().consume(cursor);

    assertEquals(new SourcePosition(3, 1), cursor.position());
    assertTrue(consumer.canConsume(cursor));

    consumer.consume(cursor);

    assertTrue(cursor.isAtEnd());
    assertEquals(new SourcePosition(3, 9), cursor.position());
  }

  @Test
  void recognizesCommentAfterPreviouslyReadText() {
    SourceCursor cursor = new SourceCursor("x // trailing");

    cursor.advance();
    cursor.advance();

    assertEquals(new SourcePosition(1, 3), cursor.position());
    assertTrue(consumer.canConsume(cursor));

    consumer.consume(cursor);

    assertTrue(cursor.isAtEnd());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "/", "/*", "a//", " //", "\"//\""})
  void rejectsInputsWithoutCommentPrefix(String input) {
    SourceCursor cursor = new SourceCursor(input);
    int originalCharacter = cursor.peek();

    assertFalse(consumer.canConsume(cursor));

    LexicalException error = assertThrows(LexicalException.class, () -> consumer.consume(cursor));

    assertEquals(new SourcePosition(1, 1), error.position());
    assertEquals(new SourcePosition(1, 1), cursor.position());
    assertEquals(originalCharacter, cursor.peek());
  }

  @Test
  void countsUnicodeCodePointsCorrectly() {
    SourceCursor cursor = new SourceCursor("//😀\nX");

    consumer.consume(cursor);

    assertEquals(new SourcePosition(1, 4), cursor.position());
    assertEquals('\n', cursor.peek());

    cursor.advance();

    assertEquals(new SourcePosition(2, 1), cursor.position());
    assertEquals('X', cursor.peek());
  }

  @Test
  void rejectsNullCursor() {
    assertThrows(NullPointerException.class, () -> consumer.canConsume(null));
    assertThrows(NullPointerException.class, () -> consumer.consume(null));
  }
}
