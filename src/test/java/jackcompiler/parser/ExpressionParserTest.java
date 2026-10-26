package jackcompiler.parser;

import static jackcompiler.parser.ExpressionParserTestSupport.assertTerm;
import static jackcompiler.parser.ExpressionParserTestSupport.cursor;
import static jackcompiler.parser.ExpressionParserTestSupport.token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.token.TokenizationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpressionParserTest {

  @Test
  void rejectsNullCursor() {
    assertThrows(NullPointerException.class, () -> new ExpressionParser(null));
  }

  @Test
  void parsesIntegerConstantAsTermWithOriginalToken() {
    Token integer = token(TokenType.INTEGER_CONSTANT, "32767", 1, 1);
    TokenCursor cursor = cursor(integer);

    GrammarNode term = new ExpressionParser(cursor).parseTerm();

    assertTerm(term, integer);
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void parsesStringConstantsWithoutChangingWhitespaceOrEmptyContents() {
    for (String value : List.of("", " Hello World ", "a < b & c")) {
      Token string = token(TokenType.STRING_CONSTANT, value, 1, 1);
      GrammarNode term = new ExpressionParser(cursor(string)).parseTerm();

      assertTerm(term, string);
      assertEquals(value, ((TerminalNode) term.children().get(0)).token().value());
    }
  }

  @Test
  void parsesIdentifiersAsAtomicTerms() {
    Token identifier = token(TokenType.IDENTIFIER, "counter_2", 2, 5);

    GrammarNode term = new ExpressionParser(cursor(identifier)).parseTerm();

    assertTerm(term, identifier);
  }

  @Test
  void acceptsExactlyTheFourJackKeywordConstants() {
    for (String keyword : List.of("true", "false", "null", "this")) {
      Token original = token(TokenType.KEYWORD, keyword, 1, 1);

      assertTerm(new ExpressionParser(cursor(original)).parseTerm(), original);
    }
  }

  @Test
  void rejectsOtherKeywordsWithoutConsumingThem() {
    for (String keyword : List.of("class", "return", "let", "int", "void")) {
      Token unexpected = token(TokenType.KEYWORD, keyword, 2, 3);
      TokenCursor cursor = cursor(unexpected);

      SyntaxException error =
          assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseTerm());

      assertEquals("term", error.expected());
      assertSame(unexpected, error.found().orElseThrow());
      assertEquals(unexpected.position(), error.position());
      assertSame(unexpected, cursor.peek().orElseThrow());
    }
  }

  @Test
  void rejectsSymbolsThatCannotStartAtomicTerms() {
    for (String symbol : List.of(";", "+", "*", ")", "[", "}")) {
      Token unexpected = token(TokenType.SYMBOL, symbol, 1, 4);
      TokenCursor cursor = cursor(unexpected);

      SyntaxException error =
          assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseTerm());

      assertSame(unexpected, error.found().orElseThrow());
      assertEquals(unexpected.position(), error.position());
      assertSame(unexpected, cursor.peek().orElseThrow());
    }
  }

  @Test
  void emptyInputReportsTheExactEofPosition() {
    SourcePosition eof = new SourcePosition(7, 9);
    TokenCursor cursor = new TokenCursor(new TokenizationResult(List.of(), eof));

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseExpression());

    assertEquals("term", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(eof, error.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void wrapsSingleAtomicTermInExpression() {
    Token value = token(TokenType.IDENTIFIER, "x", 1, 1);
    TokenCursor cursor = cursor(value);

    GrammarNode expression = new ExpressionParser(cursor).parseExpression();

    assertEquals(SyntaxTag.EXPRESSION, expression.tag());
    assertEquals(1, expression.children().size());
    assertTerm((GrammarNode) expression.children().get(0), value);
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void supportsAllNineBinaryOperatorsWithoutLosingTokens() {
    for (String symbol : List.of("+", "-", "*", "/", "&", "|", "<", ">", "=")) {
      Token left = token(TokenType.IDENTIFIER, "x", 1, 1);
      Token op = token(TokenType.SYMBOL, symbol, 1, 2);
      Token right = token(TokenType.INTEGER_CONSTANT, "2", 1, 3);
      TokenCursor cursor = cursor(left, op, right);

      GrammarNode expression = new ExpressionParser(cursor).parseExpression();

      assertEquals(SyntaxTag.EXPRESSION, expression.tag());
      assertEquals(3, expression.children().size());
      assertTerm((GrammarNode) expression.children().get(0), left);
      assertSame(op, ((TerminalNode) expression.children().get(1)).token());
      assertTerm((GrammarNode) expression.children().get(2), right);
      assertTrue(cursor.isAtEnd());
    }
  }

  @Test
  void preservesFlatLeftToRightGrammarWithoutInventingOperatorPrecedence() {
    Token a = token(TokenType.IDENTIFIER, "a", 1, 1);
    Token plus = token(TokenType.SYMBOL, "+", 1, 3);
    Token two = token(TokenType.INTEGER_CONSTANT, "2", 1, 5);
    Token star = token(TokenType.SYMBOL, "*", 1, 7);
    Token b = token(TokenType.IDENTIFIER, "b", 1, 9);
    TokenCursor cursor = cursor(a, plus, two, star, b);

    GrammarNode expression = new ExpressionParser(cursor).parseExpression();

    assertEquals(SyntaxTag.EXPRESSION, expression.tag());
    assertEquals(5, expression.children().size());
    assertTerm((GrammarNode) expression.children().get(0), a);
    assertSame(plus, ((TerminalNode) expression.children().get(1)).token());
    assertTerm((GrammarNode) expression.children().get(2), two);
    assertSame(star, ((TerminalNode) expression.children().get(3)).token());
    assertTerm((GrammarNode) expression.children().get(4), b);
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void stopsBeforeStatementDelimitersAndDoesNotConsumeThem() {
    for (String delimiter : List.of(";", ")", ",", "]", "}")) {
      Token value = token(TokenType.INTEGER_CONSTANT, "1", 1, 1);
      Token stop = token(TokenType.SYMBOL, delimiter, 1, 2);
      TokenCursor cursor = cursor(value, stop);

      GrammarNode expression = new ExpressionParser(cursor).parseExpression();

      assertEquals(1, expression.children().size());
      assertSame(stop, cursor.peek().orElseThrow());
      assertFalse(cursor.isAtEnd());
    }
  }

  @Test
  void operatorWithoutRightOperandReportsEofWithoutInventingAnEofToken() {
    Token left = token(TokenType.INTEGER_CONSTANT, "1", 1, 1);
    Token plus = token(TokenType.SYMBOL, "+", 1, 2);
    SourcePosition eof = new SourcePosition(1, 3);
    TokenCursor cursor = new TokenCursor(new TokenizationResult(List.of(left, plus), eof));

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseExpression());

    assertEquals("term", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(eof, error.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void operatorFollowedByInvalidTermReportsThatTokenAndDoesNotConsumeIt() {
    Token left = token(TokenType.IDENTIFIER, "x", 1, 1);
    Token plus = token(TokenType.SYMBOL, "+", 1, 2);
    Token semicolon = token(TokenType.SYMBOL, ";", 1, 3);
    TokenCursor cursor = cursor(left, plus, semicolon);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseExpression());

    assertEquals("term", error.expected());
    assertSame(semicolon, error.found().orElseThrow());
    assertEquals(semicolon.position(), error.position());
    assertSame(semicolon, cursor.peek().orElseThrow());
  }

  @Test
  void doesNotMistakeAnIdentifierWithOperatorSpellingForAnOperator() {
    Token left = token(TokenType.IDENTIFIER, "x", 1, 1);
    Token notAnOperator = token(TokenType.IDENTIFIER, "+", 1, 2);
    TokenCursor cursor = cursor(left, notAnOperator);

    GrammarNode expression = new ExpressionParser(cursor).parseExpression();

    assertEquals(1, expression.children().size());
    assertSame(notAnOperator, cursor.peek().orElseThrow());
  }

  @Test
  void preservesOriginalOrderAndDoesNotChangeTheLexicalResult() {
    Token a = token(TokenType.IDENTIFIER, "a", 1, 1);
    Token op = token(TokenType.SYMBOL, "&", 1, 3);
    Token b = token(TokenType.KEYWORD, "true", 1, 5);
    TokenizationResult result = new TokenizationResult(List.of(a, op, b), new SourcePosition(1, 9));
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode expression = new ExpressionParser(cursor).parseExpression();

    assertEquals(List.of(a, op, b), result.tokens());
    assertTerm((GrammarNode) expression.children().get(0), a);
    assertSame(op, ((TerminalNode) expression.children().get(1)).token());
    assertTerm((GrammarNode) expression.children().get(2), b);
  }

  @Test
  void integratesWithTheRealScannerAndPreservesOriginalStringContent() {
    TokenizationResult result = new JackTokenizer().tokenize("  \" a & b \" + 2 // comment\r\n");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode expression = new ExpressionParser(cursor).parseExpression();

    assertEquals(3, expression.children().size());
    assertEquals(" a & b ", result.tokens().get(0).value());
    assertTerm((GrammarNode) expression.children().get(0), result.tokens().get(0));
    assertSame(result.tokens().get(1), ((TerminalNode) expression.children().get(1)).token());
    assertTerm((GrammarNode) expression.children().get(2), result.tokens().get(2));
    assertTrue(cursor.isAtEnd());
    assertEquals(result.endPosition(), cursor.position());
  }
}
