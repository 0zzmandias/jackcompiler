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

class TriviaSkipperTest {

  private final TriviaSkipper skipper = new TriviaSkipper();

  @Test
  void skipsInterleavedWhitespaceAndBothCommentKinds() {
    SourceCursor cursor = new SourceCursor(" \t/*block*/\r\n//line\n \tclass");

    skipper.skip(cursor);

    assertEquals('c', cursor.peek());
    assertEquals(new SourcePosition(3, 3), cursor.position());

    Token token = new IdentifierRecognizer().recognize(cursor);

    assertEquals(TokenType.KEYWORD, token.type());
    assertEquals("class", token.value());
    assertEquals(new SourcePosition(3, 3), token.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void skipsAdjacentCommentsOfDifferentKinds() {
    SourceCursor cursor = new SourceCursor("/*a*///b\n/*c*/x");

    skipper.skip(cursor);

    assertEquals('x', cursor.peek());
    assertEquals(new SourcePosition(2, 6), cursor.position());
  }

  @ParameterizedTest
  @ValueSource(strings = {"class", "123", "\"text\"", "{", "/", "-", "_"})
  void leavesSignificantInputUntouched(String input) {
    SourceCursor cursor = new SourceCursor(input);

    skipper.skip(cursor);

    assertEquals(input.codePointAt(0), cursor.peek());
    assertEquals(new SourcePosition(1, 1), cursor.position());
    assertFalse(cursor.isAtEnd());
  }

  @Test
  void stopsBeforeFollowingSymbol() {
    SourceCursor cursor = new SourceCursor(" \t/* docs */;");

    skipper.skip(cursor);

    assertEquals(';', cursor.peek());
    assertEquals(new SourcePosition(1, 13), cursor.position());

    Token token = new SymbolRecognizer().recognize(cursor);

    assertEquals(TokenType.SYMBOL, token.type());
    assertEquals(";", token.value());
    assertEquals(new SourcePosition(1, 13), token.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void doesNotTreatCommentMarkersInsideStringAsTrivia() {
    SourceCursor cursor = new SourceCursor("\"// not a comment\"");

    skipper.skip(cursor);

    assertEquals('"', cursor.peek());
    assertEquals(new SourcePosition(1, 1), cursor.position());

    Token token = new StringRecognizer().recognize(cursor);

    assertEquals(TokenType.STRING_CONSTANT, token.type());
    assertEquals("// not a comment", token.value());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void treatsLineCommentMarkersInsideBlockAsContent() {
    SourceCursor cursor = new SourceCursor("/* // literal */x");

    skipper.skip(cursor);

    assertEquals('x', cursor.peek());
    assertFalse(cursor.isAtEnd());
  }

  @Test
  void treatsBlockCommentMarkersInsideLineAsContent() {
    SourceCursor cursor = new SourceCursor("// /* literal */\r\nx");

    skipper.skip(cursor);

    assertEquals('x', cursor.peek());
    assertEquals(new SourcePosition(2, 1), cursor.position());
  }

  @Test
  void handlesEmptySource() {
    SourceCursor cursor = new SourceCursor("");

    skipper.skip(cursor);

    assertTrue(cursor.isAtEnd());
    assertEquals(SourceCursor.EOF, cursor.peek());
    assertEquals(new SourcePosition(1, 1), cursor.position());
  }

  @ParameterizedTest
  @ValueSource(strings = {" \t\r\n", "// comment", "/* comment */", "/**/", "//"})
  void consumesOnlyTriviaUntilEndOfFile(String input) {
    SourceCursor cursor = new SourceCursor(input);

    skipper.skip(cursor);

    assertTrue(cursor.isAtEnd());
    assertEquals(SourceCursor.EOF, cursor.peek());
  }

  @Test
  void preservesPositionAcrossCrLfAndTabulation() {
    SourceCursor cursor = new SourceCursor("//x\r\n\tlet");

    skipper.skip(cursor);

    assertEquals('l', cursor.peek());
    assertEquals(new SourcePosition(2, 2), cursor.position());
  }

  @Test
  void countsUnicodeCodePointsInsideComments() {
    SourceCursor cursor = new SourceCursor("/*😀*/ x");

    skipper.skip(cursor);

    assertEquals('x', cursor.peek());
    assertEquals(new SourcePosition(1, 7), cursor.position());
  }

  @Test
  void propagatesUnterminatedBlockCommentError() {
    SourceCursor cursor = new SourceCursor(" \r\n/* missing");

    LexicalException error = assertThrows(LexicalException.class, () -> skipper.skip(cursor));

    assertEquals(new SourcePosition(2, 1), error.position());
    assertTrue(error.getMessage().contains("Unterminated"));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void leavesUnsupportedWhitespaceCharacterUntouched() {
    SourceCursor cursor = new SourceCursor(" \fclass");

    skipper.skip(cursor);

    assertEquals('\f', cursor.peek());
    assertEquals(new SourcePosition(1, 2), cursor.position());
  }

  @Test
  void worksAfterPreviouslyRecognizedToken() {
    SourceCursor cursor = new SourceCursor("var /* hi */name");

    Token first = new IdentifierRecognizer().recognize(cursor);
    assertEquals(TokenType.KEYWORD, first.type());

    skipper.skip(cursor);

    assertEquals(new SourcePosition(1, 13), cursor.position());

    Token second = new IdentifierRecognizer().recognize(cursor);

    assertEquals(TokenType.IDENTIFIER, second.type());
    assertEquals("name", second.value());
    assertEquals(new SourcePosition(1, 13), second.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void repeatedCallsDoNotConsumeSignificantInput() {
    SourceCursor cursor = new SourceCursor("  x");

    skipper.skip(cursor);
    assertEquals(new SourcePosition(1, 3), cursor.position());

    skipper.skip(cursor);

    assertEquals('x', cursor.peek());
    assertEquals(new SourcePosition(1, 3), cursor.position());
  }

  @Test
  void rejectsNullCursor() {
    assertThrows(NullPointerException.class, () -> skipper.skip(null));
  }
}
