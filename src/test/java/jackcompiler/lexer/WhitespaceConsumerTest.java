package jackcompiler.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.token.SourcePosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WhitespaceConsumerTest {

  private final WhitespaceConsumer consumer = new WhitespaceConsumer();

  @ParameterizedTest
  @ValueSource(strings = {" ", "\t", "\n", "\r", "\r\n", " \t\r\n "})
  void consumesSupportedWhitespace(String whitespace) {
    SourceCursor cursor = new SourceCursor(whitespace + "x");

    assertTrue(consumer.canConsume(cursor));
    assertEquals(new SourcePosition(1, 1), cursor.position());

    consumer.consume(cursor);

    assertEquals('x', cursor.peek());
    assertFalse(consumer.canConsume(cursor));
  }

  @Test
  void handlesMixedWhitespaceAndLineEndings() {
    SourceCursor cursor = new SourceCursor(" \t\r\n \r\n\tX");

    consumer.consume(cursor);

    assertEquals('X', cursor.peek());
    assertEquals(new SourcePosition(3, 2), cursor.position());
  }

  @Test
  void stopsBeforeFollowingIdentifier() {
    SourceCursor cursor = new SourceCursor("  \tclass");

    consumer.consume(cursor);

    assertEquals('c', cursor.peek());
    assertEquals(new SourcePosition(1, 4), cursor.position());
  }

  @Test
  void leavesCommentStartUntouched() {
    SourceCursor cursor = new SourceCursor(" \t// comment");

    consumer.consume(cursor);

    assertTrue(cursor.startsWith("//"));
    assertEquals(new SourcePosition(1, 3), cursor.position());
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "0", "/", "😀"})
  void rejectsNonWhitespaceStarts(String input) {
    SourceCursor cursor = new SourceCursor(input);

    assertFalse(consumer.canConsume(cursor));

    LexicalException error = assertThrows(LexicalException.class, () -> consumer.consume(cursor));

    assertEquals(new SourcePosition(1, 1), error.position());
    assertEquals(new SourcePosition(1, 1), cursor.position());
    assertFalse(cursor.isAtEnd());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\f", "\u00A0", "\u2003"})
  void rejectsCharactersOutsideJackWhitespace(String input) {
    SourceCursor cursor = new SourceCursor(input);

    assertFalse(consumer.canConsume(cursor));

    assertThrows(LexicalException.class, () -> consumer.consume(cursor));

    assertEquals(new SourcePosition(1, 1), cursor.position());
  }

  @Test
  void consumesWhitespaceUntilEndOfFile() {
    SourceCursor cursor = new SourceCursor("\t\r\n");

    consumer.consume(cursor);

    assertTrue(cursor.isAtEnd());
    assertEquals(SourceCursor.EOF, cursor.peek());
    assertEquals(new SourcePosition(2, 1), cursor.position());
  }

  @Test
  void rejectsConsumptionAtEndOfFile() {
    SourceCursor cursor = new SourceCursor("");

    assertFalse(consumer.canConsume(cursor));

    LexicalException error = assertThrows(LexicalException.class, () -> consumer.consume(cursor));

    assertEquals(new SourcePosition(1, 1), error.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void consumesWhitespaceAfterPreviouslyReadContent() {
    SourceCursor cursor = new SourceCursor("abc\r\n \t;");

    for (int i = 0; i < 4; i++) {
      cursor.advance();
    }

    assertEquals(new SourcePosition(2, 1), cursor.position());

    consumer.consume(cursor);

    assertEquals(';', cursor.peek());
    assertEquals(new SourcePosition(2, 3), cursor.position());
  }

  @Test
  void checkingWhitespaceDoesNotAdvanceCursor() {
    SourceCursor cursor = new SourceCursor(" \t");

    assertTrue(consumer.canConsume(cursor));
    assertTrue(consumer.canConsume(cursor));

    assertEquals(' ', cursor.peek());
    assertEquals(new SourcePosition(1, 1), cursor.position());
  }

  @Test
  void rejectsNullCursor() {
    assertThrows(NullPointerException.class, () -> consumer.canConsume(null));
    assertThrows(NullPointerException.class, () -> consumer.consume(null));
  }
}
