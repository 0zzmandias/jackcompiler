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

class IdentifierRecognizerTest {

  private final IdentifierRecognizer recognizer = new IdentifierRecognizer();

  @ParameterizedTest
  @ValueSource(
      strings = {
        "class",
        "constructor",
        "function",
        "method",
        "field",
        "static",
        "var",
        "int",
        "char",
        "boolean",
        "void",
        "true",
        "false",
        "null",
        "this",
        "let",
        "do",
        "if",
        "else",
        "while",
        "return"
      })
  void recognizesEveryJackKeyword(String keyword) {
    SourceCursor cursor = new SourceCursor(keyword);

    assertTrue(recognizer.canRecognize(cursor));
    assertEquals(new SourcePosition(1, 1), cursor.position());

    Token token = recognizer.recognize(cursor);

    assertEquals(TokenType.KEYWORD, token.type());
    assertEquals(keyword, token.value());
    assertEquals(new SourcePosition(1, 1), token.position());
    assertTrue(cursor.isAtEnd());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "main",
        "Class",
        "CLASS",
        "className",
        "returnValue",
        "_",
        "_9",
        "abc123",
        "TRUE",
        "constructor2",
        "x_y"
      })
  void recognizesValidIdentifiers(String identifier) {
    SourceCursor cursor = new SourceCursor(identifier);

    Token token = recognizer.recognize(cursor);

    assertEquals(TokenType.IDENTIFIER, token.type());
    assertEquals(identifier, token.value());
    assertEquals(new SourcePosition(1, 1), token.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void doesNotTreatKeywordPrefixesAsKeywords() {
    SourceCursor cursor = new SourceCursor("class99");

    Token token = recognizer.recognize(cursor);

    assertEquals(TokenType.IDENTIFIER, token.type());
    assertEquals("class99", token.value());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void stopsBeforeFollowingSymbolWithoutConsumingIt() {
    SourceCursor cursor = new SourceCursor("counter;");

    Token token = recognizer.recognize(cursor);

    assertEquals(TokenType.IDENTIFIER, token.type());
    assertEquals("counter", token.value());
    assertEquals(';', cursor.peek());
    assertEquals(new SourcePosition(1, 8), cursor.position());
  }

  @Test
  void acceptsDigitsAfterTheFirstIdentifierCharacter() {
    SourceCursor cursor = new SourceCursor("_123abc456");

    Token token = recognizer.recognize(cursor);

    assertEquals(TokenType.IDENTIFIER, token.type());
    assertEquals("_123abc456", token.value());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void preservesPositionAfterLineBreak() {
    SourceCursor cursor = new SourceCursor("noop\r\nreturn");

    for (int i = 0; i < 5; i++) {
      cursor.advance();
    }

    Token token = recognizer.recognize(cursor);

    assertEquals(TokenType.KEYWORD, token.type());
    assertEquals("return", token.value());
    assertEquals(new SourcePosition(2, 1), token.position());
    assertEquals(new SourcePosition(2, 7), cursor.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void stopsBeforeUnsupportedUnicodeCharacter() {
    SourceCursor cursor = new SourceCursor("abc😀");

    Token token = recognizer.recognize(cursor);

    assertEquals(TokenType.IDENTIFIER, token.type());
    assertEquals("abc", token.value());
    assertEquals(0x1F600, cursor.peek());
    assertEquals(new SourcePosition(1, 4), cursor.position());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "9", "{", "😀"})
  void rejectsInvalidIdentifierStarts(String input) {
    SourceCursor cursor = new SourceCursor(input);

    assertFalse(recognizer.canRecognize(cursor));

    LexicalException error =
        assertThrows(LexicalException.class, () -> recognizer.recognize(cursor));

    assertEquals(new SourcePosition(1, 1), error.position());
    assertEquals(new SourcePosition(1, 1), cursor.position());
    assertFalse(cursor.isAtEnd());
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
  void rejectsInvalidStartWithoutConsumingInput() {
    SourceCursor cursor = new SourceCursor("123abc");

    LexicalException error =
        assertThrows(LexicalException.class, () -> recognizer.recognize(cursor));

    assertEquals(new SourcePosition(1, 1), error.position());
    assertEquals('1', cursor.peek());
    assertEquals(new SourcePosition(1, 1), cursor.position());
  }

  @Test
  void rejectsNullCursor() {
    assertThrows(NullPointerException.class, () -> recognizer.canRecognize(null));
    assertThrows(NullPointerException.class, () -> recognizer.recognize(null));
  }
}
