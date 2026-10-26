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
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.token.TokenizationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpressionParserExpressionListTest {

  @Test
  void parsesEmptyListWithoutConsumingClosingParenthesis() {
    Token close = token(TokenType.SYMBOL, ")", 1, 8);
    TokenCursor cursor = cursor(close);

    GrammarNode list = new ExpressionParser(cursor).parseExpressionList();

    assertEquals(SyntaxTag.EXPRESSION_LIST, list.tag());
    assertTrue(list.children().isEmpty());
    assertSame(close, cursor.peek().orElseThrow());
  }

  @Test
  void parsesOneArgumentWithoutConsumingClosingParenthesis() {
    TokenizationResult result = new JackTokenizer().tokenize("x)");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode list = new ExpressionParser(cursor).parseExpressionList();

    GrammarNode expression = assertGrammar(list.children().get(0), SyntaxTag.EXPRESSION, 1);
    assertEquals(1, list.children().size());
    assertTerm((GrammarNode) expression.children().get(0), result.tokens().get(0));
    assertSame(result.tokens().get(1), cursor.peek().orElseThrow());
  }

  @Test
  void parsesSeveralArgumentsWithCommaTerminalsAndFlatExpressions() {
    TokenizationResult result = new JackTokenizer().tokenize("x + 1, 2, true)");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode list = new ExpressionParser(cursor).parseExpressionList();

    assertEquals(5, list.children().size());
    assertGrammar(list.children().get(0), SyntaxTag.EXPRESSION, 3);
    assertTerminal(list.children().get(1), result.tokens().get(3));
    assertGrammar(list.children().get(2), SyntaxTag.EXPRESSION, 1);
    assertTerminal(list.children().get(3), result.tokens().get(5));
    assertGrammar(list.children().get(4), SyntaxTag.EXPRESSION, 1);
    assertSame(result.tokens().get(7), cursor.peek().orElseThrow());
  }

  @Test
  void doesNotTreatCommasInsideStringsAsArgumentSeparators() {
    TokenizationResult result = new JackTokenizer().tokenize("\"hello, world\", 2)");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode list = new ExpressionParser(cursor).parseExpressionList();

    assertEquals(3, list.children().size());
    GrammarNode first = assertGrammar(list.children().get(0), SyntaxTag.EXPRESSION, 1);
    assertTerm((GrammarNode) first.children().get(0), result.tokens().get(0));
    assertEquals("hello, world", result.tokens().get(0).value());
    assertTerminal(list.children().get(1), result.tokens().get(1));
    assertSame(result.tokens().get(3), cursor.peek().orElseThrow());
  }

  @Test
  void keepsNestedCallCommasSeparateFromOuterArgumentList() {
    TokenizationResult result = new JackTokenizer().tokenize("f(a, b), g())");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode list = new ExpressionParser(cursor).parseExpressionList();

    assertEquals(3, list.children().size());
    GrammarNode firstExpression = assertGrammar(list.children().get(0), SyntaxTag.EXPRESSION, 1);
    GrammarNode firstCall = assertGrammar(firstExpression.children().get(0), SyntaxTag.TERM, 4);
    assertGrammar(firstCall.children().get(2), SyntaxTag.EXPRESSION_LIST, 3);
    assertTerminal(list.children().get(1), result.tokens().get(6));
    GrammarNode secondExpression = assertGrammar(list.children().get(2), SyntaxTag.EXPRESSION, 1);
    GrammarNode secondCall = assertGrammar(secondExpression.children().get(0), SyntaxTag.TERM, 4);
    assertGrammar(secondCall.children().get(2), SyntaxTag.EXPRESSION_LIST, 0);
    assertSame(result.tokens().get(10), cursor.peek().orElseThrow());
  }

  @Test
  void acceptsArraysUnaryOperatorsAndGroupingAsArguments() {
    TokenizationResult result = new JackTokenizer().tokenize("arr[i + 1], -(x))");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode list = new ExpressionParser(cursor).parseExpressionList();

    assertEquals(3, list.children().size());
    GrammarNode first = assertGrammar(list.children().get(0), SyntaxTag.EXPRESSION, 1);
    assertGrammar(first.children().get(0), SyntaxTag.TERM, 4);
    GrammarNode second = assertGrammar(list.children().get(2), SyntaxTag.EXPRESSION, 1);
    assertGrammar(second.children().get(0), SyntaxTag.TERM, 2);
    assertEquals(")", cursor.peek().orElseThrow().value());
  }

  @Test
  void rejectsLeadingCommaWithoutAdvancingPastIt() {
    TokenizationResult result = new JackTokenizer().tokenize(",a)");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(
            SyntaxException.class, () -> new ExpressionParser(cursor).parseExpressionList());

    assertEquals("term", error.expected());
    assertSame(result.tokens().get(0), error.found().orElseThrow());
    assertSame(result.tokens().get(0), cursor.peek().orElseThrow());
  }

  @Test
  void rejectsTrailingCommaWithoutConsumingClosingParenthesis() {
    TokenizationResult result = new JackTokenizer().tokenize("x,)");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(
            SyntaxException.class, () -> new ExpressionParser(cursor).parseExpressionList());

    assertEquals("term", error.expected());
    assertSame(result.tokens().get(2), error.found().orElseThrow());
    assertSame(result.tokens().get(2), cursor.peek().orElseThrow());
  }

  @Test
  void rejectsConsecutiveCommasWithoutConsumingTheSecondOne() {
    TokenizationResult result = new JackTokenizer().tokenize("x,,y)");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(
            SyntaxException.class, () -> new ExpressionParser(cursor).parseExpressionList());

    assertEquals("term", error.expected());
    assertSame(result.tokens().get(2), error.found().orElseThrow());
    assertSame(result.tokens().get(2), cursor.peek().orElseThrow());
  }

  @Test
  void reportsEofWhenAnArgumentExpressionIsExpected() {
    SourcePosition eof = new SourcePosition(9, 12);
    TokenCursor cursor = new TokenCursor(new TokenizationResult(List.of(), eof));

    SyntaxException error =
        assertThrows(
            SyntaxException.class, () -> new ExpressionParser(cursor).parseExpressionList());

    assertEquals("term", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(eof, error.position());
  }

  @Test
  void rejectsClosingBracketInsteadOfTreatingItAsAnEmptyList() {
    Token unexpected = token(TokenType.SYMBOL, "]", 4, 7);
    TokenCursor cursor = cursor(unexpected);

    SyntaxException error =
        assertThrows(
            SyntaxException.class, () -> new ExpressionParser(cursor).parseExpressionList());

    assertEquals("term", error.expected());
    assertSame(unexpected, error.found().orElseThrow());
    assertSame(unexpected, cursor.peek().orElseThrow());
  }
}
