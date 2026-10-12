package jackcompiler.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.token.TokenizationResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JackTokenizerTest {

  private final JackTokenizer tokenizer = new JackTokenizer();

  @Test
  void tokenizesCompleteClassDeclaration() {
    String source = "class Main { function void main() { return; } }";

    TokenizationResult result = tokenizer.tokenize(source);

    assertEquals(
        List.of(
            "class",
            "Main",
            "{",
            "function",
            "void",
            "main",
            "(",
            ")",
            "{",
            "return",
            ";",
            "}",
            "}"),
        values(result));

    assertEquals(TokenType.KEYWORD, result.tokens().get(0).type());
    assertEquals(TokenType.IDENTIFIER, result.tokens().get(1).type());
    assertEquals(TokenType.SYMBOL, result.tokens().get(2).type());
    assertEquals(new SourcePosition(1, 48), result.endPosition());
  }

  @Test
  void recognizesAllFiveTokenCategories() {
    String source = "let value = 32767; do Output.printString(\"Hi <&>\");";

    TokenizationResult result = tokenizer.tokenize(source);

    assertEquals(
        List.of(
            "let",
            "value",
            "=",
            "32767",
            ";",
            "do",
            "Output",
            ".",
            "printString",
            "(",
            "Hi <&>",
            ")",
            ";"),
        values(result));

    assertEquals(
        Set.of(TokenType.values()), Set.copyOf(result.tokens().stream().map(Token::type).toList()));
  }

  @Test
  void skipsCommentsBeforeBetweenAndAfterTokens() {
    String source = "/*h*/class/**/ Main//x\r\n{ return; }/*tail*/";

    TokenizationResult result = tokenizer.tokenize(source);

    assertEquals(List.of("class", "Main", "{", "return", ";", "}"), values(result));
    assertEquals(TokenType.KEYWORD, result.tokens().get(0).type());
    assertEquals(TokenType.IDENTIFIER, result.tokens().get(1).type());
  }

  @Test
  void preservesPositionsAcrossLineEndingsAndComments() {
    String source = "// heading\r\n  class/*x*/ Main\r\n{ }";

    TokenizationResult result = tokenizer.tokenize(source);

    assertEquals(List.of("class", "Main", "{", "}"), values(result));
    assertEquals(new SourcePosition(2, 3), result.tokens().get(0).position());
    assertEquals(new SourcePosition(2, 14), result.tokens().get(1).position());
    assertEquals(new SourcePosition(3, 1), result.tokens().get(2).position());
    assertEquals(new SourcePosition(3, 3), result.tokens().get(3).position());
    assertEquals(new SourcePosition(3, 4), result.endPosition());
  }

  @Test
  void preservesCommentMarkersInsideStringConstants() {
    String source = "let text = \"// /* keep */\";";

    TokenizationResult result = tokenizer.tokenize(source);

    assertEquals(List.of("let", "text", "=", "// /* keep */", ";"), values(result));
    assertEquals(TokenType.STRING_CONSTANT, result.tokens().get(3).type());
  }

  @Test
  void distinguishesSlashSymbolsFromComments() {
    String source = "a/b; a/*c*/ /b; //end";

    TokenizationResult result = tokenizer.tokenize(source);

    assertEquals(List.of("a", "/", "b", ";", "a", "/", "b", ";"), values(result));
    assertEquals(TokenType.SYMBOL, result.tokens().get(1).type());
    assertEquals(TokenType.SYMBOL, result.tokens().get(5).type());
  }

  @Test
  void preservesLeadingZerosInIntegerConstants() {
    TokenizationResult result = tokenizer.tokenize("let n = 00032767;");

    assertEquals(List.of("let", "n", "=", "00032767", ";"), values(result));
    assertEquals(TokenType.INTEGER_CONSTANT, result.tokens().get(3).type());
  }

  @Test
  void treatsMinusAsSymbolRatherThanPartOfInteger() {
    TokenizationResult result = tokenizer.tokenize("-32767");

    assertEquals(List.of("-", "32767"), values(result));
    assertEquals(TokenType.SYMBOL, result.tokens().get(0).type());
    assertEquals(TokenType.INTEGER_CONSTANT, result.tokens().get(1).type());
    assertEquals(new SourcePosition(1, 7), result.endPosition());
  }

  @Test
  void returnsEmptyTokenSequenceForEmptySource() {
    TokenizationResult result = tokenizer.tokenize("");

    assertTrue(result.tokens().isEmpty());
    assertEquals(new SourcePosition(1, 1), result.endPosition());
  }

  @Test
  void returnsEmptyTokenSequenceForTriviaOnlySource() {
    TokenizationResult result = tokenizer.tokenize(" \t/*block*/\r\n//line");

    assertTrue(result.tokens().isEmpty());
    assertEquals(new SourcePosition(2, 7), result.endPosition());
  }

  @Test
  void tracksEndPositionAfterTrailingComments() {
    TokenizationResult result = tokenizer.tokenize("x//end\r\n");

    assertEquals(List.of("x"), values(result));
    assertEquals(new SourcePosition(1, 1), result.tokens().get(0).position());
    assertEquals(new SourcePosition(2, 1), result.endPosition());
  }

  @Test
  void tracksUnicodeCodePointPositions() {
    TokenizationResult result = tokenizer.tokenize("/*😀*/ \"Olá 😀\";");

    assertEquals(List.of("Olá 😀", ";"), values(result));
    assertEquals(new SourcePosition(1, 7), result.tokens().get(0).position());
    assertEquals(new SourcePosition(1, 14), result.tokens().get(1).position());
    assertEquals(new SourcePosition(1, 15), result.endPosition());
  }

  @ParameterizedTest
  @ValueSource(strings = {"@", "?", "á", "😀", "\f"})
  void rejectsUnexpectedCharacters(String invalid) {
    LexicalException error =
        assertThrows(LexicalException.class, () -> tokenizer.tokenize("class " + invalid));

    assertEquals(new SourcePosition(1, 7), error.position());
    assertTrue(error.getMessage().contains("U+"));
  }

  @Test
  void propagatesUnterminatedBlockCommentError() {
    LexicalException error =
        assertThrows(LexicalException.class, () -> tokenizer.tokenize("class A {} /* never"));

    assertEquals(new SourcePosition(1, 12), error.position());
    assertTrue(error.getMessage().contains("Unterminated"));
  }

  @Test
  void propagatesUnterminatedStringError() {
    LexicalException error =
        assertThrows(LexicalException.class, () -> tokenizer.tokenize("let x = \"abc"));

    assertEquals(new SourcePosition(1, 9), error.position());
    assertTrue(error.getMessage().contains("Unterminated"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r", "\r\n"})
  void rejectsLineBreaksInsideStringConstants(String lineBreak) {
    LexicalException error =
        assertThrows(
            LexicalException.class, () -> tokenizer.tokenize("\"abc" + lineBreak + "tail\""));

    assertEquals(new SourcePosition(1, 5), error.position());
    assertTrue(error.getMessage().contains("Line breaks"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"32768", "00032768", "999999999999999"})
  void rejectsOutOfRangeIntegerConstants(String integer) {
    LexicalException error =
        assertThrows(LexicalException.class, () -> tokenizer.tokenize("let x = " + integer + ";"));

    assertEquals(new SourcePosition(1, 9), error.position());
    assertTrue(error.getMessage().contains("32767"));
  }

  @Test
  void rejectsNullSource() {
    assertThrows(NullPointerException.class, () -> tokenizer.tokenize(null));
  }

  @Test
  void supportsRepeatedIndependentTokenizations() {
    TokenizationResult first = tokenizer.tokenize("class");
    TokenizationResult second = tokenizer.tokenize("123");

    assertEquals(List.of("class"), values(first));
    assertEquals(List.of("123"), values(second));

    assertEquals(TokenType.KEYWORD, first.tokens().get(0).type());
    assertEquals(TokenType.INTEGER_CONSTANT, second.tokens().get(0).type());

    assertEquals(new SourcePosition(1, 6), first.endPosition());
    assertEquals(new SourcePosition(1, 4), second.endPosition());
  }

  @Test
  void reportsInvalidCharacterOnFollowingLine() {
    LexicalException error =
        assertThrows(LexicalException.class, () -> tokenizer.tokenize("let x = 0;\n😀"));

    assertEquals(new SourcePosition(2, 1), error.position());
  }

  @Test
  void recognizesAdjacentStringsAndIdentifier() {
    TokenizationResult result = tokenizer.tokenize("\"a\"\"b\"name");

    assertEquals(List.of("a", "b", "name"), values(result));

    assertEquals(TokenType.STRING_CONSTANT, result.tokens().get(0).type());
    assertEquals(TokenType.STRING_CONSTANT, result.tokens().get(1).type());
    assertEquals(TokenType.IDENTIFIER, result.tokens().get(2).type());

    assertEquals(new SourcePosition(1, 1), result.tokens().get(0).position());
    assertEquals(new SourcePosition(1, 4), result.tokens().get(1).position());
    assertEquals(new SourcePosition(1, 7), result.tokens().get(2).position());
  }

  private static List<String> values(TokenizationResult result) {
    return result.tokens().stream().map(Token::value).toList();
  }
}
