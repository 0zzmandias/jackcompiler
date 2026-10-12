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

class BlockCommentConsumerTest {

  private final BlockCommentConsumer consumer = new BlockCommentConsumer();

  @ParameterizedTest
  @ValueSource(strings = {"/*", "/**", "/* content */"})
  void detectsBlockCommentStartWithoutAdvancing(String input) {
    SourceCursor cursor = new SourceCursor(input);

    assertTrue(consumer.canConsume(cursor));
    assertTrue(consumer.canConsume(cursor));
    assertEquals(new SourcePosition(1, 1), cursor.position());
    assertEquals('/', cursor.peek());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/* */",
        "/**/",
        "/** docs */",
        "/* // text */",
        "/* 😀 */",
        "/*a**/",
        "/* \"text\" */"
      })
  void consumesValidBlockComments(String input) {
    SourceCursor cursor = new SourceCursor(input);

    consumer.consume(cursor);

    assertTrue(cursor.isAtEnd());
    assertEquals(SourceCursor.EOF, cursor.peek());
    assertEquals(
        new SourcePosition(1, input.codePointCount(0, input.length()) + 1), cursor.position());
  }

  @Test
  void stopsImmediatelyAfterClosingDelimiter() {
    SourceCursor cursor = new SourceCursor("/* docs */class");

    consumer.consume(cursor);

    assertEquals('c', cursor.peek());
    assertEquals(new SourcePosition(1, 11), cursor.position());
    assertFalse(consumer.canConsume(cursor));
  }

  @Test
  void tracksMixedLineEndingsCorrectly() {
    SourceCursor cursor = new SourceCursor("/*a\r\nb\nc\rd*/;");

    consumer.consume(cursor);

    assertEquals(';', cursor.peek());
    assertEquals(new SourcePosition(4, 4), cursor.position());
  }

  @Test
  void consumesAdjacentBlockCommentsSeparately() {
    SourceCursor cursor = new SourceCursor("/*a*//*b*/;");

    consumer.consume(cursor);

    assertTrue(consumer.canConsume(cursor));
    assertEquals(new SourcePosition(1, 6), cursor.position());

    consumer.consume(cursor);

    assertEquals(';', cursor.peek());
    assertEquals(new SourcePosition(1, 11), cursor.position());
  }

  @Test
  void treatsLineCommentMarkersAsBlockCommentContent() {
    SourceCursor cursor = new SourceCursor("/* // line\ninside */return");

    consumer.consume(cursor);

    assertEquals('r', cursor.peek());
    assertEquals(new SourcePosition(2, 10), cursor.position());
  }

  @Test
  void doesNotInterpretNestedOpeningDelimiterAsNewComment() {
    SourceCursor cursor = new SourceCursor("/* outer /* inner */class");

    consumer.consume(cursor);

    assertEquals('c', cursor.peek());
    assertFalse(consumer.canConsume(cursor));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/*", "/**", "/*abc", "/*/", "/*a\nb", "/*abc*"})
  void rejectsUnterminatedBlockComments(String input) {
    SourceCursor cursor = new SourceCursor(input);

    LexicalException error = assertThrows(LexicalException.class, () -> consumer.consume(cursor));

    assertEquals(new SourcePosition(1, 1), error.position());
    assertTrue(error.getMessage().contains("Unterminated"));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void reportsOpeningPositionForUnterminatedComment() {
    SourceCursor cursor = new SourceCursor("x\r\n  /*missing");

    for (int i = 0; i < 4; i++) {
      cursor.advance();
    }

    assertEquals(new SourcePosition(2, 3), cursor.position());

    LexicalException error = assertThrows(LexicalException.class, () -> consumer.consume(cursor));

    assertEquals(new SourcePosition(2, 3), error.position());
    assertTrue(cursor.isAtEnd());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "/", "// line", "* /", " /*", "a/*"})
  void rejectsInputsWithoutOpeningDelimiter(String input) {
    SourceCursor cursor = new SourceCursor(input);
    int originalCharacter = cursor.peek();

    assertFalse(consumer.canConsume(cursor));

    LexicalException error = assertThrows(LexicalException.class, () -> consumer.consume(cursor));

    assertEquals(new SourcePosition(1, 1), error.position());
    assertEquals(new SourcePosition(1, 1), cursor.position());
    assertEquals(originalCharacter, cursor.peek());
  }

  @Test
  void leavesFollowingLineBreakForWhitespaceConsumer() {
    SourceCursor cursor = new SourceCursor("/*done*/\r\nlet");

    consumer.consume(cursor);

    assertEquals('\n', cursor.peek());
    assertEquals(new SourcePosition(1, 9), cursor.position());

    new WhitespaceConsumer().consume(cursor);

    Token token = new IdentifierRecognizer().recognize(cursor);

    assertEquals(TokenType.KEYWORD, token.type());
    assertEquals("let", token.value());
    assertEquals(new SourcePosition(2, 1), token.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void rejectsNullCursor() {
    assertThrows(NullPointerException.class, () -> consumer.canConsume(null));
    assertThrows(NullPointerException.class, () -> consumer.consume(null));
  }
}
