package jackcompiler.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SyntaxExceptionTest {

  @Test
  void reportsExpectedElementUnexpectedTokenAndItsPosition() {
    SourcePosition position = new SourcePosition(3, 12);
    Token token = new Token(TokenType.SYMBOL, ";", position);

    SyntaxException exception = new SyntaxException("identifier", Optional.of(token), position);

    assertEquals("identifier", exception.expected());
    assertSame(token, exception.found().orElseThrow());
    assertEquals(position, exception.position());
    assertEquals("Expected identifier, but found SYMBOL ';'.", exception.getMessage());
  }

  @Test
  void reportsUnexpectedEndOfInputAtTheScannerEndPosition() {
    SourcePosition eofPosition = new SourcePosition(5, 1);

    SyntaxException exception = new SyntaxException("symbol '}'", Optional.empty(), eofPosition);

    assertEquals("symbol '}'", exception.expected());
    assertTrue(exception.found().isEmpty());
    assertEquals(eofPosition, exception.position());
    assertEquals("Expected symbol '}', but found end of input (EOF).", exception.getMessage());
  }

  @Test
  void isUncheckedLikeTheExistingLexicalException() {
    SyntaxException exception =
        new SyntaxException("class declaration", Optional.empty(), new SourcePosition(1, 1));

    assertTrue(exception instanceof RuntimeException);
  }

  @Test
  void rejectsNullExpectation() {
    assertThrows(
        NullPointerException.class,
        () -> new SyntaxException(null, Optional.empty(), new SourcePosition(1, 1)));
  }

  @Test
  void rejectsBlankExpectation() {
    SourcePosition position = new SourcePosition(1, 1);

    assertThrows(
        IllegalArgumentException.class, () -> new SyntaxException("", Optional.empty(), position));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SyntaxException(" \t ", Optional.empty(), position));
  }

  @Test
  void rejectsNullUnexpectedTokenContainer() {
    assertThrows(
        NullPointerException.class,
        () -> new SyntaxException("identifier", null, new SourcePosition(1, 1)));
  }

  @Test
  void rejectsNullPosition() {
    assertThrows(
        NullPointerException.class,
        () -> new SyntaxException("identifier", Optional.empty(), null));
  }

  @Test
  void rejectsAnErrorPositionDifferentFromTheUnexpectedTokenPosition() {
    Token token = new Token(TokenType.IDENTIFIER, "name", new SourcePosition(2, 4));

    assertThrows(
        IllegalArgumentException.class,
        () -> new SyntaxException("symbol ';'", Optional.of(token), new SourcePosition(2, 5)));
  }
}
