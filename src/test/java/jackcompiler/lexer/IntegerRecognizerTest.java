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

class IntegerRecognizerTest {

  private final IntegerRecognizer recognizer = new IntegerRecognizer();

  @ParameterizedTest
  @ValueSource(strings = {"0", "1", "9", "10", "123", "32767", "00032767", "0000", "00123"})
  void recognizesValidIntegerConstants(String input) {
    SourceCursor cursor = new SourceCursor(input);

    assertTrue(recognizer.canRecognize(cursor));
    assertEquals(new SourcePosition(1, 1), cursor.position());

    Token token = recognizer.recognize(cursor);

    assertEquals(TokenType.INTEGER_CONSTANT, token.type());
    assertEquals(input, token.value());
    assertEquals(new SourcePosition(1, 1), token.position());
    assertTrue(cursor.isAtEnd());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"32768", "65535", "99999", "00032768", "2147483648", "9999999999999999999999"})
  void rejectsIntegerConstantsAboveJackLimit(String input) {
    SourceCursor cursor = new SourceCursor(input);

    LexicalException error =
        assertThrows(LexicalException.class, () -> recognizer.recognize(cursor));

    assertEquals(new SourcePosition(1, 1), error.position());
    assertTrue(error.getMessage().contains("32767"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "abc", "_abc", "-", ";", "😀", "١"})
  void rejectsInvalidIntegerStarts(String input) {
    SourceCursor cursor = new SourceCursor(input);

    assertFalse(recognizer.canRecognize(cursor));

    LexicalException error =
        assertThrows(LexicalException.class, () -> recognizer.recognize(cursor));

    assertEquals(new SourcePosition(1, 1), error.position());
    assertEquals(new SourcePosition(1, 1), cursor.position());
  }

  @Test
  void stopsBeforeFollowingSymbol() {
    SourceCursor cursor = new SourceCursor("123;");

    Token token = recognizer.recognize(cursor);

    assertEquals("123", token.value());
    assertEquals(';', cursor.peek());
    assertEquals(new SourcePosition(1, 4), cursor.position());
  }

  @Test
  void allowsIdentifierToFollowInteger() {
    SourceCursor cursor = new SourceCursor("123abc");

    Token integer = recognizer.recognize(cursor);

    assertEquals(TokenType.INTEGER_CONSTANT, integer.type());
    assertEquals("123", integer.value());
    assertEquals('a', cursor.peek());

    Token identifier = new IdentifierRecognizer().recognize(cursor);

    assertEquals(TokenType.IDENTIFIER, identifier.type());
    assertEquals("abc", identifier.value());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void doesNotConsumeNegativeSign() {
    SourceCursor cursor = new SourceCursor("-123");

    assertFalse(recognizer.canRecognize(cursor));
    assertEquals('-', cursor.peek());

    Token minus = new SymbolRecognizer().recognize(cursor);
    Token integer = recognizer.recognize(cursor);

    assertEquals(TokenType.SYMBOL, minus.type());
    assertEquals("-", minus.value());
    assertEquals(TokenType.INTEGER_CONSTANT, integer.type());
    assertEquals("123", integer.value());
    assertEquals(new SourcePosition(1, 2), integer.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void preservesPositionAfterLineBreak() {
    SourceCursor cursor = new SourceCursor("hello\r\n123;");

    for (int i = 0; i < 6; i++) {
      cursor.advance();
    }

    Token token = recognizer.recognize(cursor);

    assertEquals("123", token.value());
    assertEquals(new SourcePosition(2, 1), token.position());
    assertEquals(new SourcePosition(2, 4), cursor.position());
    assertEquals(';', cursor.peek());
  }

  @Test
  void rejectsNullCursor() {
    assertThrows(NullPointerException.class, () -> recognizer.canRecognize(null));
    assertThrows(NullPointerException.class, () -> recognizer.recognize(null));
  }
}
