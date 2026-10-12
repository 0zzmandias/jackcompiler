package jackcompiler.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.token.SourcePosition;
import org.junit.jupiter.api.Test;

class SourceCursorTest {

  @Test
  void startsAtFirstPosition() {
    SourceCursor cursor = new SourceCursor("class");

    assertEquals(new SourcePosition(1, 1), cursor.position());
    assertEquals('c', cursor.peek());
    assertFalse(cursor.isAtEnd());
  }

  @Test
  void readsSupplementaryUnicodeAsOneColumn() {
    SourceCursor cursor = new SourceCursor("A😀B");

    assertEquals('A', cursor.advance());
    assertEquals(new SourcePosition(1, 2), cursor.position());

    assertEquals(0x1F600, cursor.advance());
    assertEquals(new SourcePosition(1, 3), cursor.position());

    assertEquals('B', cursor.advance());
    assertEquals(new SourcePosition(1, 4), cursor.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void tracksAllSupportedLineEndings() {
    SourceCursor cursor = new SourceCursor("A\r\nB\nC\rD");

    assertEquals('A', cursor.advance());
    assertEquals(new SourcePosition(1, 2), cursor.position());

    assertEquals('\n', cursor.advance());
    assertEquals(new SourcePosition(2, 1), cursor.position());

    assertEquals('B', cursor.advance());
    assertEquals(new SourcePosition(2, 2), cursor.position());

    assertEquals('\n', cursor.advance());
    assertEquals(new SourcePosition(3, 1), cursor.position());

    assertEquals('C', cursor.advance());
    assertEquals(new SourcePosition(3, 2), cursor.position());

    assertEquals('\n', cursor.advance());
    assertEquals(new SourcePosition(4, 1), cursor.position());

    assertEquals('D', cursor.advance());
    assertEquals(new SourcePosition(4, 2), cursor.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void looksAheadWithoutAdvancing() {
    SourceCursor cursor = new SourceCursor("A😀\r\nB");

    assertEquals('A', cursor.peek());
    assertEquals(0x1F600, cursor.peek(1));
    assertEquals('\n', cursor.peek(2));
    assertEquals('B', cursor.peek(3));
    assertEquals(SourceCursor.EOF, cursor.peek(4));
    assertEquals(SourceCursor.EOF, cursor.peek(100));

    assertEquals(new SourcePosition(1, 1), cursor.position());
    assertFalse(cursor.isAtEnd());
  }

  @Test
  void matchesPrefixesWithoutConsumingCharacters() {
    SourceCursor cursor = new SourceCursor("/* hi */\r\n😀");

    assertTrue(cursor.startsWith("/*"));
    assertTrue(cursor.startsWith("/* hi */\n😀"));
    assertTrue(cursor.startsWith("/* hi */\r\n😀"));
    assertTrue(cursor.startsWith(""));
    assertFalse(cursor.startsWith("//"));
    assertFalse(cursor.startsWith("/* hi */\nA"));

    assertEquals(new SourcePosition(1, 1), cursor.position());
    assertEquals('/', cursor.peek());
  }

  @Test
  void reportsCorrectPositionAtEndOfFile() {
    SourceCursor cursor = new SourceCursor("x\r\n");

    assertEquals('x', cursor.advance());
    assertEquals('\n', cursor.advance());

    assertTrue(cursor.isAtEnd());
    assertEquals(SourceCursor.EOF, cursor.peek());
    assertEquals(new SourcePosition(2, 1), cursor.position());
    assertTrue(cursor.startsWith(""));
    assertFalse(cursor.startsWith("x"));
  }

  @Test
  void handlesEmptySource() {
    SourceCursor cursor = new SourceCursor("");

    assertTrue(cursor.isAtEnd());
    assertEquals(SourceCursor.EOF, cursor.peek());
    assertEquals(SourceCursor.EOF, cursor.peek(5));
    assertEquals(new SourcePosition(1, 1), cursor.position());
  }

  @Test
  void rejectsAdvancingBeyondEndOfFile() {
    SourceCursor cursor = new SourceCursor("");

    assertThrows(IllegalStateException.class, cursor::advance);
  }

  @Test
  void rejectsNegativeLookahead() {
    SourceCursor cursor = new SourceCursor("abc");

    assertThrows(IllegalArgumentException.class, () -> cursor.peek(-1));
  }

  @Test
  void rejectsNullArguments() {
    assertThrows(NullPointerException.class, () -> new SourceCursor(null));

    SourceCursor cursor = new SourceCursor("abc");

    assertThrows(NullPointerException.class, () -> cursor.startsWith(null));
  }
}
