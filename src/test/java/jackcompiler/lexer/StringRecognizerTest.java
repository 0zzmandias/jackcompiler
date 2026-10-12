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

class StringRecognizerTest {

  private final StringRecognizer recognizer = new StringRecognizer();

  @ParameterizedTest
  @ValueSource(strings = {"Hello", "Hello world", "a\tb", "// text", "/* text */", "<>&", "Olá 😀"})
  void recognizesValidStringConstants(String content) {
    SourceCursor cursor = new SourceCursor("\"" + content + "\"");

    assertTrue(recognizer.canRecognize(cursor));
    assertEquals(new SourcePosition(1, 1), cursor.position());

    Token token = recognizer.recognize(cursor);

    assertEquals(TokenType.STRING_CONSTANT, token.type());
    assertEquals(content, token.value());
    assertEquals(new SourcePosition(1, 1), token.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void recognizesEmptyString() {
    SourceCursor cursor = new SourceCursor("\"\"");

    Token token = recognizer.recognize(cursor);

    assertEquals(TokenType.STRING_CONSTANT, token.type());
    assertEquals("", token.value());
    assertEquals(new SourcePosition(1, 3), cursor.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void preservesPositionAfterLineBreak() {
    SourceCursor cursor = new SourceCursor("ignored\r\n\"Hi\"");

    for (int i = 0; i < 8; i++) {
      cursor.advance();
    }

    Token token = recognizer.recognize(cursor);

    assertEquals(TokenType.STRING_CONSTANT, token.type());
    assertEquals("Hi", token.value());
    assertEquals(new SourcePosition(2, 1), token.position());
    assertEquals(new SourcePosition(2, 5), cursor.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void stopsAfterClosingQuotationMark() {
    SourceCursor cursor = new SourceCursor("\"abc\";");

    Token token = recognizer.recognize(cursor);

    assertEquals("abc", token.value());
    assertEquals(';', cursor.peek());
    assertEquals(new SourcePosition(1, 6), cursor.position());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "abc", "0", "{", "😀"})
  void rejectsInvalidStringStarts(String input) {
    SourceCursor cursor = new SourceCursor(input);

    assertFalse(recognizer.canRecognize(cursor));

    LexicalException error =
        assertThrows(LexicalException.class, () -> recognizer.recognize(cursor));

    assertEquals(new SourcePosition(1, 1), error.position());
    assertEquals(new SourcePosition(1, 1), cursor.position());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"", "\"abc", "\"/* comment */", "\"Olá 😀"})
  void rejectsUnterminatedStrings(String input) {
    SourceCursor cursor = new SourceCursor(input);

    LexicalException error =
        assertThrows(LexicalException.class, () -> recognizer.recognize(cursor));

    assertEquals(new SourcePosition(1, 1), error.position());
    assertTrue(error.getMessage().contains("Unterminated"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"a\nb\"", "\"a\rb\"", "\"a\r\nb\""})
  void rejectsLineBreaksInsideStrings(String input) {
    SourceCursor cursor = new SourceCursor(input);

    LexicalException error =
        assertThrows(LexicalException.class, () -> recognizer.recognize(cursor));

    assertEquals(new SourcePosition(1, 3), error.position());
    assertEquals('\n', cursor.peek());
    assertTrue(error.getMessage().contains("Line breaks"));
  }

  @Test
  void preservesBackslashesWithoutInterpretingEscapes() {
    SourceCursor cursor = new SourceCursor("\"a\\nb\"");

    Token token = recognizer.recognize(cursor);

    assertEquals("a\\nb", token.value());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void recognizesConsecutiveStringsSeparately() {
    SourceCursor cursor = new SourceCursor("\"a\"\"b\"");

    Token first = recognizer.recognize(cursor);
    Token second = recognizer.recognize(cursor);

    assertEquals("a", first.value());
    assertEquals(new SourcePosition(1, 1), first.position());

    assertEquals("b", second.value());
    assertEquals(new SourcePosition(1, 4), second.position());

    assertTrue(cursor.isAtEnd());
  }

  @Test
  void rejectsNullCursor() {
    assertThrows(NullPointerException.class, () -> recognizer.canRecognize(null));
    assertThrows(NullPointerException.class, () -> recognizer.recognize(null));
  }
}
