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

class SymbolRecognizerTest {

  private final SymbolRecognizer recognizer = new SymbolRecognizer();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{", "}", "(", ")", "[", "]", ".", ",", ";", "+", "-", "*", "/", "&", "|", "<", ">", "=",
        "~"
      })
  void recognizesEveryJackSymbol(String symbol) {
    SourceCursor cursor = new SourceCursor(symbol + "x");

    assertTrue(recognizer.canRecognize(cursor));
    assertEquals(new SourcePosition(1, 1), cursor.position());

    Token token = recognizer.recognize(cursor);

    assertEquals(TokenType.SYMBOL, token.type());
    assertEquals(symbol, token.value());
    assertEquals(new SourcePosition(1, 1), token.position());

    assertEquals('x', cursor.peek());
    assertEquals(new SourcePosition(1, 2), cursor.position());
  }

  @ParameterizedTest
  @ValueSource(strings = {"A", "0", " ", "?", "😀"})
  void rejectsCharactersOutsideTheSymbolSet(String value) {
    SourceCursor cursor = new SourceCursor(value);

    assertFalse(recognizer.canRecognize(cursor));
    assertEquals(new SourcePosition(1, 1), cursor.position());
  }

  @Test
  void reportsInvalidSymbolWithoutConsumingInput() {
    SourceCursor cursor = new SourceCursor("abc");

    LexicalException error =
        assertThrows(LexicalException.class, () -> recognizer.recognize(cursor));

    assertEquals(new SourcePosition(1, 1), error.position());
    assertEquals('a', cursor.peek());
    assertEquals(new SourcePosition(1, 1), cursor.position());
  }

  @Test
  void rejectsRecognitionAtEndOfFile() {
    SourceCursor cursor = new SourceCursor("");

    assertFalse(recognizer.canRecognize(cursor));

    LexicalException error =
        assertThrows(LexicalException.class, () -> recognizer.recognize(cursor));

    assertEquals(new SourcePosition(1, 1), error.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void recognizesSlashWithoutConsumingFollowingAsterisk() {
    SourceCursor cursor = new SourceCursor("/*");

    Token token = recognizer.recognize(cursor);

    assertEquals(TokenType.SYMBOL, token.type());
    assertEquals("/", token.value());
    assertEquals('*', cursor.peek());
  }

  @Test
  void preservesPositionAfterLineBreak() {
    SourceCursor cursor = new SourceCursor("abc\r\n;");

    for (int i = 0; i < 4; i++) {
      cursor.advance();
    }

    Token token = recognizer.recognize(cursor);

    assertEquals(";", token.value());
    assertEquals(new SourcePosition(2, 1), token.position());
    assertEquals(new SourcePosition(2, 2), cursor.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void rejectsNullCursor() {
    assertThrows(NullPointerException.class, () -> recognizer.canRecognize(null));
    assertThrows(NullPointerException.class, () -> recognizer.recognize(null));
  }
}
