package jackcompiler.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.token.TokenizationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenCursorTest {

  private static final Token FIRST = token(TokenType.KEYWORD, "class", 1, 1);
  private static final Token SECOND = token(TokenType.IDENTIFIER, "Main", 1, 7);
  private static final Token THIRD = token(TokenType.SYMBOL, "{", 1, 12);
  private static final SourcePosition END = new SourcePosition(2, 1);

  @Test
  void rejectsNullTokenizationResult() {
    assertThrows(NullPointerException.class, () -> new TokenCursor(null));
  }

  @Test
  void startsAtFirstTokenWithoutConsumingIt() {
    TokenCursor cursor = cursorWithThreeTokens();

    assertFalse(cursor.isAtEnd());
    assertSame(FIRST, cursor.peek().orElseThrow());
    assertEquals(FIRST.position(), cursor.position());
  }

  @Test
  void peekDoesNotAdvanceEvenWhenCalledRepeatedly() {
    TokenCursor cursor = cursorWithThreeTokens();

    assertSame(FIRST, cursor.peek().orElseThrow());
    assertSame(FIRST, cursor.peek().orElseThrow());
    assertEquals(FIRST.position(), cursor.position());
  }

  @Test
  void lookaheadUsesOffsetsRelativeToTheCurrentToken() {
    TokenCursor cursor = cursorWithThreeTokens();

    assertSame(FIRST, cursor.peek(0).orElseThrow());
    assertSame(SECOND, cursor.peek(1).orElseThrow());
    assertSame(THIRD, cursor.peek(2).orElseThrow());
    assertSame(FIRST, cursor.peek().orElseThrow());

    cursor.advance();

    assertSame(SECOND, cursor.peek(0).orElseThrow());
    assertSame(THIRD, cursor.peek(1).orElseThrow());
    assertEquals(SECOND.position(), cursor.position());
  }

  @Test
  void lookaheadReturnsEmptyWhenTheOffsetIsBeyondTheAvailableTokens() {
    TokenCursor cursor = cursorWithThreeTokens();

    assertTrue(cursor.peek(3).isEmpty());
    assertTrue(cursor.peek(Integer.MAX_VALUE).isEmpty());
    assertSame(FIRST, cursor.peek().orElseThrow());

    cursor.advance();
    cursor.advance();
    assertTrue(cursor.peek(1).isEmpty());
  }

  @Test
  void rejectsNegativeLookaheadWithoutMovingTheCursor() {
    TokenCursor cursor = cursorWithThreeTokens();

    assertThrows(IllegalArgumentException.class, () -> cursor.peek(-1));
    assertSame(FIRST, cursor.peek().orElseThrow());
  }

  @Test
  void matchesTokenTypeWithoutConsuming() {
    TokenCursor cursor = cursorWithThreeTokens();

    assertTrue(cursor.match(TokenType.KEYWORD));
    assertFalse(cursor.match(TokenType.IDENTIFIER));
    assertSame(FIRST, cursor.peek().orElseThrow());
  }

  @Test
  void matchesTokenTypeAndExactValueWithoutConsuming() {
    TokenCursor cursor = cursorWithThreeTokens();

    assertTrue(cursor.match(TokenType.KEYWORD, "class"));
    assertFalse(cursor.match(TokenType.KEYWORD, "Class"));
    assertFalse(cursor.match(TokenType.IDENTIFIER, "class"));
    assertSame(FIRST, cursor.peek().orElseThrow());
  }

  @Test
  void rejectsNullMatchArguments() {
    TokenCursor cursor = cursorWithThreeTokens();

    assertThrows(NullPointerException.class, () -> cursor.match(null));
    assertThrows(NullPointerException.class, () -> cursor.match(null, "class"));
    assertThrows(NullPointerException.class, () -> cursor.match(TokenType.KEYWORD, null));
    assertSame(FIRST, cursor.peek().orElseThrow());
  }

  @Test
  void advanceReturnsOriginalTokensAndConsumesExactlyOneAtATime() {
    TokenCursor cursor = cursorWithThreeTokens();

    assertSame(FIRST, cursor.advance());
    assertSame(SECOND, cursor.peek().orElseThrow());
    assertSame(SECOND, cursor.advance());
    assertSame(THIRD, cursor.advance());
    assertTrue(cursor.isAtEnd());
    assertTrue(cursor.peek().isEmpty());
    assertEquals(END, cursor.position());
  }

  @Test
  void advanceAtEndThrowsPositionedSyntaxExceptionWithoutMoving() {
    TokenCursor cursor = emptyCursor();

    SyntaxException exception = assertThrows(SyntaxException.class, cursor::advance);

    assertEquals("token", exception.expected());
    assertTrue(exception.found().isEmpty());
    assertEquals(END, exception.position());
    assertTrue(cursor.isAtEnd());
    assertEquals(END, cursor.position());
  }

  @Test
  void consumeTypeReturnsOriginalTokenAndAdvances() {
    TokenCursor cursor = cursorWithThreeTokens();

    assertSame(FIRST, cursor.consume(TokenType.KEYWORD));
    assertSame(SECOND, cursor.peek().orElseThrow());
  }

  @Test
  void consumeTypeAndValueReturnsOriginalTokenAndAdvances() {
    TokenCursor cursor = cursorWithThreeTokens();

    assertSame(FIRST, cursor.consume(TokenType.KEYWORD, "class"));
    assertSame(SECOND, cursor.peek().orElseThrow());
    assertSame(SECOND, cursor.consume(TokenType.IDENTIFIER, "Main"));
    assertSame(THIRD, cursor.peek().orElseThrow());
  }

  @Test
  void consumeWrongTypeReportsFoundTokenAndDoesNotAdvance() {
    TokenCursor cursor = cursorWithThreeTokens();

    SyntaxException exception =
        assertThrows(SyntaxException.class, () -> cursor.consume(TokenType.IDENTIFIER));

    assertEquals("token of type IDENTIFIER", exception.expected());
    assertSame(FIRST, exception.found().orElseThrow());
    assertEquals(FIRST.position(), exception.position());
    assertSame(FIRST, cursor.peek().orElseThrow());
  }

  @Test
  void consumeWrongValueReportsFoundTokenAndDoesNotAdvance() {
    TokenCursor cursor = cursorWithThreeTokens();

    SyntaxException exception =
        assertThrows(SyntaxException.class, () -> cursor.consume(TokenType.KEYWORD, "return"));

    assertEquals("KEYWORD 'return'", exception.expected());
    assertSame(FIRST, exception.found().orElseThrow());
    assertEquals(FIRST.position(), exception.position());
    assertSame(FIRST, cursor.peek().orElseThrow());
  }

  @Test
  void consumeWrongTypeAndCorrectValueDoesNotAdvance() {
    TokenCursor cursor = cursorWithThreeTokens();

    SyntaxException exception =
        assertThrows(SyntaxException.class, () -> cursor.consume(TokenType.IDENTIFIER, "class"));

    assertSame(FIRST, exception.found().orElseThrow());
    assertSame(FIRST, cursor.peek().orElseThrow());
  }

  @Test
  void consumeTypeAtEndReportsOriginalEofPosition() {
    TokenCursor cursor = emptyCursor();

    SyntaxException exception =
        assertThrows(SyntaxException.class, () -> cursor.consume(TokenType.IDENTIFIER));

    assertEquals("token of type IDENTIFIER", exception.expected());
    assertTrue(exception.found().isEmpty());
    assertEquals(END, exception.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void consumeTypeAndValueAtEndReportsOriginalEofPosition() {
    TokenCursor cursor = emptyCursor();

    SyntaxException exception =
        assertThrows(SyntaxException.class, () -> cursor.consume(TokenType.SYMBOL, "}"));

    assertEquals("SYMBOL '}'", exception.expected());
    assertTrue(exception.found().isEmpty());
    assertEquals(END, exception.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void rejectsNullConsumeArgumentsWithoutMoving() {
    TokenCursor cursor = cursorWithThreeTokens();

    assertThrows(NullPointerException.class, () -> cursor.consume(null));
    assertThrows(NullPointerException.class, () -> cursor.consume(null, ";"));
    assertThrows(NullPointerException.class, () -> cursor.consume(TokenType.SYMBOL, null));
    assertSame(FIRST, cursor.peek().orElseThrow());
  }

  @Test
  void emptyTokenStreamPreservesEofAndExposesNoMatches() {
    TokenCursor cursor = emptyCursor();

    assertTrue(cursor.isAtEnd());
    assertTrue(cursor.peek().isEmpty());
    assertTrue(cursor.peek(0).isEmpty());
    assertTrue(cursor.peek(100).isEmpty());
    assertFalse(cursor.match(TokenType.KEYWORD));
    assertFalse(cursor.match(TokenType.SYMBOL, "}"));
    assertEquals(END, cursor.position());
  }

  @Test
  void twoCursorsOverOneResultHaveIndependentProgress() {
    TokenizationResult result = resultWithThreeTokens();
    TokenCursor first = new TokenCursor(result);
    TokenCursor second = new TokenCursor(result);

    first.advance();
    first.advance();

    assertSame(THIRD, first.peek().orElseThrow());
    assertSame(FIRST, second.peek().orElseThrow());
    assertEquals(List.of(FIRST, SECOND, THIRD), result.tokens());
  }

  @Test
  void worksWithRealScannerAndPreservesPositionsAcrossCommentsAndLineBreaks() {
    TokenizationResult result = new JackTokenizer().tokenize("// comment\r\nclass Main {}\n");
    TokenCursor cursor = new TokenCursor(result);

    assertEquals(new SourcePosition(2, 1), cursor.position());
    assertEquals("class", cursor.consume(TokenType.KEYWORD).value());
    assertEquals(new SourcePosition(2, 7), cursor.position());
    assertEquals("Main", cursor.consume(TokenType.IDENTIFIER).value());
    assertEquals("{", cursor.consume(TokenType.SYMBOL, "{").value());
    assertEquals("}", cursor.consume(TokenType.SYMBOL, "}").value());
    assertTrue(cursor.isAtEnd());
    assertEquals(new SourcePosition(3, 1), cursor.position());
    assertEquals(result.endPosition(), cursor.position());
  }

  private static TokenCursor cursorWithThreeTokens() {
    return new TokenCursor(resultWithThreeTokens());
  }

  private static TokenizationResult resultWithThreeTokens() {
    return new TokenizationResult(List.of(FIRST, SECOND, THIRD), END);
  }

  private static TokenCursor emptyCursor() {
    return new TokenCursor(new TokenizationResult(List.of(), END));
  }

  private static Token token(TokenType type, String value, int line, int column) {
    return new Token(type, value, new SourcePosition(line, column));
  }
}
