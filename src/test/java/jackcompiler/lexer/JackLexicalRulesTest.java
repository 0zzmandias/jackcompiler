package jackcompiler.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JackLexicalRulesTest {

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
  void recognizesAllJackKeywords(String keyword) {
    assertTrue(JackLexicalRules.isKeyword(keyword));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{", "}", "(", ")", "[", "]", ".", ",", ";", "+", "-", "*", "/", "&", "|", "<", ">", "=",
        "~"
      })
  void recognizesAllJackSymbols(String symbol) {
    assertTrue(JackLexicalRules.isSymbol(symbol.charAt(0)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Class", "CLASS", "While", "returnValue", "className",
        "constructor2", "integer", "falseValue", "main", ""
      })
  void rejectsNonKeywords(String value) {
    assertFalse(JackLexicalRules.isKeyword(value));
  }

  @Test
  void rejectsCharactersOutsideJackSymbols() {
    assertFalse(JackLexicalRules.isSymbol('!'));
    assertFalse(JackLexicalRules.isSymbol('?'));
    assertFalse(JackLexicalRules.isSymbol(':'));
    assertFalse(JackLexicalRules.isSymbol('@'));
    assertFalse(JackLexicalRules.isSymbol('"'));
    assertFalse(JackLexicalRules.isSymbol('\\'));
    assertFalse(JackLexicalRules.isSymbol(0x1F600));
    assertFalse(JackLexicalRules.isSymbol(SourceCursor.EOF));
  }

  @Test
  void recognizesValidIdentifierCharacters() {
    assertTrue(JackLexicalRules.isIdentifierStart('A'));
    assertTrue(JackLexicalRules.isIdentifierStart('Z'));
    assertTrue(JackLexicalRules.isIdentifierStart('a'));
    assertTrue(JackLexicalRules.isIdentifierStart('z'));
    assertTrue(JackLexicalRules.isIdentifierStart('_'));

    assertTrue(JackLexicalRules.isIdentifierPart('A'));
    assertTrue(JackLexicalRules.isIdentifierPart('_'));
    assertTrue(JackLexicalRules.isIdentifierPart('0'));
    assertTrue(JackLexicalRules.isIdentifierPart('9'));
  }

  @Test
  void rejectsInvalidIdentifierCharacters() {
    assertFalse(JackLexicalRules.isIdentifierStart('0'));
    assertFalse(JackLexicalRules.isIdentifierStart('9'));
    assertFalse(JackLexicalRules.isIdentifierStart('á'));
    assertFalse(JackLexicalRules.isIdentifierStart(0x1F600));

    assertFalse(JackLexicalRules.isIdentifierPart('á'));
    assertFalse(JackLexicalRules.isIdentifierPart('-'));
    assertFalse(JackLexicalRules.isIdentifierPart(' '));
    assertFalse(JackLexicalRules.isIdentifierPart(0x1F600));
  }

  @Test
  void acceptsOnlyAsciiDecimalDigits() {
    assertTrue(JackLexicalRules.isDecimalDigit('0'));
    assertTrue(JackLexicalRules.isDecimalDigit('9'));

    assertFalse(JackLexicalRules.isDecimalDigit('/'));
    assertFalse(JackLexicalRules.isDecimalDigit(':'));
    assertFalse(JackLexicalRules.isDecimalDigit('A'));
    assertFalse(JackLexicalRules.isDecimalDigit(0x0661));
  }

  @Test
  void recognizesSupportedWhitespace() {
    assertTrue(JackLexicalRules.isWhitespace(' '));
    assertTrue(JackLexicalRules.isWhitespace('\t'));
    assertTrue(JackLexicalRules.isWhitespace('\n'));
    assertTrue(JackLexicalRules.isWhitespace('\r'));

    assertFalse(JackLexicalRules.isWhitespace('A'));
    assertFalse(JackLexicalRules.isWhitespace('/'));
    assertFalse(JackLexicalRules.isWhitespace('\f'));
  }

  @Test
  void definesJackIntegerLimit() {
    assertEquals(32767, JackLexicalRules.MAX_INTEGER_CONSTANT);
  }
}
