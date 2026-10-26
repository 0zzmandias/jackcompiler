package jackcompiler.parser;

import static jackcompiler.parser.ExpressionParserTestSupport.assertGrammar;
import static jackcompiler.parser.ExpressionParserTestSupport.assertTerm;
import static jackcompiler.parser.ExpressionParserTestSupport.assertTerminal;
import static jackcompiler.parser.ExpressionParserTestSupport.cursor;
import static jackcompiler.parser.ExpressionParserTestSupport.token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.token.TokenizationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpressionParserRecursiveTermTest {

  @Test
  void parsesParenthesizedExpressionWithExactConcreteTree() {
    Token open = token(TokenType.SYMBOL, "(", 1, 1);
    Token value = token(TokenType.INTEGER_CONSTANT, "7", 1, 2);
    Token close = token(TokenType.SYMBOL, ")", 1, 3);
    TokenCursor cursor = cursor(open, value, close);

    GrammarNode term = new ExpressionParser(cursor).parseTerm();

    assertEquals(SyntaxTag.TERM, term.tag());
    assertEquals(3, term.children().size());
    assertTerminal(term.children().get(0), open);
    GrammarNode expression = assertGrammar(term.children().get(1), SyntaxTag.EXPRESSION, 1);
    assertTerm((GrammarNode) expression.children().get(0), value);
    assertTerminal(term.children().get(2), close);
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void parsesMultipleLevelsOfParenthesizedTerms() {
    TokenizationResult result = new JackTokenizer().tokenize("(((x)))");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode expression = new ExpressionParser(cursor).parseExpression();

    GrammarNode outerTerm = assertGrammar(expression.children().get(0), SyntaxTag.TERM, 3);
    GrammarNode middleExpression =
        assertGrammar(outerTerm.children().get(1), SyntaxTag.EXPRESSION, 1);
    GrammarNode middleTerm = assertGrammar(middleExpression.children().get(0), SyntaxTag.TERM, 3);
    GrammarNode innerExpression =
        assertGrammar(middleTerm.children().get(1), SyntaxTag.EXPRESSION, 1);
    GrammarNode innerTerm = assertGrammar(innerExpression.children().get(0), SyntaxTag.TERM, 3);
    GrammarNode leafExpression =
        assertGrammar(innerTerm.children().get(1), SyntaxTag.EXPRESSION, 1);
    assertTerm((GrammarNode) leafExpression.children().get(0), result.tokens().get(3));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void groupingCreatesNestedExpressionInsteadOfArtificialPrecedence() {
    TokenizationResult result = new JackTokenizer().tokenize("(a + 2) * b");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode expression = new ExpressionParser(cursor).parseExpression();

    assertEquals(3, expression.children().size());
    GrammarNode grouped = assertGrammar(expression.children().get(0), SyntaxTag.TERM, 3);
    GrammarNode inside = assertGrammar(grouped.children().get(1), SyntaxTag.EXPRESSION, 3);
    assertTerm((GrammarNode) inside.children().get(0), result.tokens().get(1));
    assertTerminal(inside.children().get(1), result.tokens().get(2));
    assertTerm((GrammarNode) inside.children().get(2), result.tokens().get(3));
    assertTerminal(expression.children().get(1), result.tokens().get(5));
    assertTerm((GrammarNode) expression.children().get(2), result.tokens().get(6));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void parsesBothUnaryOperatorsAsTerminalFollowedByTerm() {
    for (String symbol : List.of("-", "~")) {
      Token unary = token(TokenType.SYMBOL, symbol, 1, 1);
      Token operand = token(TokenType.IDENTIFIER, "x", 1, 2);
      TokenCursor cursor = cursor(unary, operand);

      GrammarNode term = new ExpressionParser(cursor).parseTerm();

      assertEquals(SyntaxTag.TERM, term.tag());
      assertEquals(2, term.children().size());
      assertTerminal(term.children().get(0), unary);
      assertTerm((GrammarNode) term.children().get(1), operand);
      assertTrue(cursor.isAtEnd());
    }
  }

  @Test
  void parsesChainedUnaryOperatorsRecursively() {
    TokenizationResult result = new JackTokenizer().tokenize("-~--x");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode term = new ExpressionParser(cursor).parseTerm();

    for (int i = 0; i < 4; i++) {
      assertEquals(SyntaxTag.TERM, term.tag());
      assertEquals(2, term.children().size());
      assertTerminal(term.children().get(0), result.tokens().get(i));
      term = assertGrammar(term.children().get(1), SyntaxTag.TERM, i == 3 ? 1 : 2);
    }
    assertTerm(term, result.tokens().get(4));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void unaryOperatorCanApplyToGroupedExpression() {
    TokenizationResult result = new JackTokenizer().tokenize("~(a = 0)");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode expression = new ExpressionParser(cursor).parseExpression();

    GrammarNode unary = assertGrammar(expression.children().get(0), SyntaxTag.TERM, 2);
    assertTerminal(unary.children().get(0), result.tokens().get(0));
    GrammarNode grouped = assertGrammar(unary.children().get(1), SyntaxTag.TERM, 3);
    GrammarNode inner = assertGrammar(grouped.children().get(1), SyntaxTag.EXPRESSION, 3);
    assertTerminal(inner.children().get(1), result.tokens().get(3));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void binaryMinusAndUnaryMinusRemainDistinctNodes() {
    TokenizationResult result = new JackTokenizer().tokenize("a - -b");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode expression = new ExpressionParser(cursor).parseExpression();

    assertEquals(3, expression.children().size());
    assertTerm((GrammarNode) expression.children().get(0), result.tokens().get(0));
    assertTerminal(expression.children().get(1), result.tokens().get(1));
    GrammarNode unary = assertGrammar(expression.children().get(2), SyntaxTag.TERM, 2);
    assertTerminal(unary.children().get(0), result.tokens().get(2));
    assertTerm((GrammarNode) unary.children().get(1), result.tokens().get(3));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void parenthesizedExpressionMissingClosingDelimiterReportsUnexpectedToken() {
    TokenizationResult result = new JackTokenizer().tokenize("(a + 1;");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseTerm());

    assertEquals("SYMBOL ')'", error.expected());
    assertSame(result.tokens().get(4), error.found().orElseThrow());
    assertEquals(result.tokens().get(4).position(), error.position());
    assertSame(result.tokens().get(4), cursor.peek().orElseThrow());
  }

  @Test
  void parenthesizedExpressionMissingClosingDelimiterReportsEof() {
    TokenizationResult result = new JackTokenizer().tokenize("(a + 1");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseTerm());

    assertEquals("SYMBOL ')'", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(result.endPosition(), error.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void emptyParenthesesReportExpectedTermAndPreserveClosingToken() {
    TokenizationResult result = new JackTokenizer().tokenize("()");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseTerm());

    assertEquals("term", error.expected());
    assertSame(result.tokens().get(1), error.found().orElseThrow());
    assertSame(result.tokens().get(1), cursor.peek().orElseThrow());
  }

  @Test
  void unaryOperatorWithoutOperandReportsEof() {
    TokenizationResult result = new JackTokenizer().tokenize("~");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseTerm());

    assertEquals("term", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(result.endPosition(), error.position());
  }

  @Test
  void unaryOperatorFollowedByInvalidTokenDoesNotConsumeIt() {
    TokenizationResult result = new JackTokenizer().tokenize("-;");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseTerm());

    assertEquals("term", error.expected());
    assertSame(result.tokens().get(1), error.found().orElseThrow());
    assertSame(result.tokens().get(1), cursor.peek().orElseThrow());
  }

  @Test
  void preservesOuterDelimiterAfterNestedExpression() {
    TokenizationResult result = new JackTokenizer().tokenize("(a[2] + 1),");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode expression = new ExpressionParser(cursor).parseExpression();

    assertEquals(1, expression.children().size());
    assertSame(result.tokens().get(8), cursor.peek().orElseThrow());
    assertEquals(",", cursor.peek().orElseThrow().value());
  }
}
