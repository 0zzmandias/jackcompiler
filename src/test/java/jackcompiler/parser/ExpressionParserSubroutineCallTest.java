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
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.token.TokenizationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpressionParserSubroutineCallTest {

  @Test
  void parsesDirectCallWithEmptyExpressionList() {
    TokenizationResult result = new JackTokenizer().tokenize("compute()");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode term = new ExpressionParser(cursor).parseTerm();

    assertEquals(SyntaxTag.TERM, term.tag());
    assertEquals(4, term.children().size());
    assertTerminal(term.children().get(0), result.tokens().get(0));
    assertTerminal(term.children().get(1), result.tokens().get(1));
    assertGrammar(term.children().get(2), SyntaxTag.EXPRESSION_LIST, 0);
    assertTerminal(term.children().get(3), result.tokens().get(2));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void parsesQualifiedCallWithEmptyExpressionList() {
    TokenizationResult result = new JackTokenizer().tokenize("Math.random()");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode term = new ExpressionParser(cursor).parseTerm();

    assertEquals(6, term.children().size());
    assertTerminal(term.children().get(0), result.tokens().get(0));
    assertTerminal(term.children().get(1), result.tokens().get(1));
    assertTerminal(term.children().get(2), result.tokens().get(2));
    assertTerminal(term.children().get(3), result.tokens().get(3));
    assertGrammar(term.children().get(4), SyntaxTag.EXPRESSION_LIST, 0);
    assertTerminal(term.children().get(5), result.tokens().get(4));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void preservesStringArgumentAndTokensOfQualifiedCall() {
    TokenizationResult result = new JackTokenizer().tokenize("output.printString(\" a, b \")");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode term = new ExpressionParser(cursor).parseTerm();

    GrammarNode arguments = assertGrammar(term.children().get(4), SyntaxTag.EXPRESSION_LIST, 1);
    GrammarNode expression = assertGrammar(arguments.children().get(0), SyntaxTag.EXPRESSION, 1);
    assertTerm((GrammarNode) expression.children().get(0), result.tokens().get(4));
    assertEquals(" a, b ", result.tokens().get(4).value());
    assertTerminal(term.children().get(5), result.tokens().get(5));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void nestsQualifiedCallsInsideArgumentExpressions() {
    TokenizationResult result =
        new JackTokenizer().tokenize("Output.printInt(Math.multiply(a + 1, b))");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode outerCall = new ExpressionParser(cursor).parseTerm();

    GrammarNode outerArguments =
        assertGrammar(outerCall.children().get(4), SyntaxTag.EXPRESSION_LIST, 1);
    GrammarNode outerExpression =
        assertGrammar(outerArguments.children().get(0), SyntaxTag.EXPRESSION, 1);
    GrammarNode innerCall = assertGrammar(outerExpression.children().get(0), SyntaxTag.TERM, 6);
    GrammarNode innerArguments =
        assertGrammar(innerCall.children().get(4), SyntaxTag.EXPRESSION_LIST, 3);
    GrammarNode first = assertGrammar(innerArguments.children().get(0), SyntaxTag.EXPRESSION, 3);
    assertTerminal(first.children().get(1), result.tokens().get(9));
    assertTerminal(innerArguments.children().get(1), result.tokens().get(11));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void parsesSubroutineCallsInsideUnaryAndArrayIndexTerms() {
    TokenizationResult result = new JackTokenizer().tokenize("-values[Math.multiply(i, 2)]");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode unary = new ExpressionParser(cursor).parseTerm();

    assertEquals(2, unary.children().size());
    assertTerminal(unary.children().get(0), result.tokens().get(0));
    GrammarNode array = assertGrammar(unary.children().get(1), SyntaxTag.TERM, 4);
    GrammarNode index = assertGrammar(array.children().get(2), SyntaxTag.EXPRESSION, 1);
    GrammarNode call = assertGrammar(index.children().get(0), SyntaxTag.TERM, 6);
    assertGrammar(call.children().get(4), SyntaxTag.EXPRESSION_LIST, 3);
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void maintainsFlatExpressionStructureWithTwoCalls() {
    TokenizationResult result = new JackTokenizer().tokenize("f(1) + g(2)");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode expression = new ExpressionParser(cursor).parseExpression();

    assertEquals(3, expression.children().size());
    assertGrammar(expression.children().get(0), SyntaxTag.TERM, 4);
    assertTerminal(expression.children().get(1), result.tokens().get(4));
    assertGrammar(expression.children().get(2), SyntaxTag.TERM, 4);
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void doesNotTreatPlainIdentifiersOrArrayAccessAsSubroutineCalls() {
    TokenizationResult result = new JackTokenizer().tokenize("values[i] + total");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode expression = new ExpressionParser(cursor).parseExpression();

    assertGrammar(expression.children().get(0), SyntaxTag.TERM, 4);
    assertTerminal(expression.children().get(1), result.tokens().get(4));
    assertTerm((GrammarNode) expression.children().get(2), result.tokens().get(5));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void exposesUnwrappedCallNodesForStatementParserWithoutConsumingSemicolon() {
    TokenizationResult result = new JackTokenizer().tokenize("Output.printInt(3);");
    TokenCursor cursor = new TokenCursor(result);

    List<ParseNode> call = new ExpressionParser(cursor).parseSubroutineCall();

    assertEquals(6, call.size());
    assertTerminal(call.get(0), result.tokens().get(0));
    assertGrammar(call.get(4), SyntaxTag.EXPRESSION_LIST, 1);
    assertTerminal(call.get(5), result.tokens().get(5));
    assertSame(result.tokens().get(6), cursor.peek().orElseThrow());
    assertThrows(UnsupportedOperationException.class, () -> call.add(call.get(0)));
  }

  @Test
  void rejectsNonIdentifierAtBeginningOfStandaloneCall() {
    Token unexpected = token(TokenType.INTEGER_CONSTANT, "2", 3, 5);
    TokenCursor cursor = cursor(unexpected);

    SyntaxException error =
        assertThrows(
            SyntaxException.class, () -> new ExpressionParser(cursor).parseSubroutineCall());

    assertEquals("token of type IDENTIFIER", error.expected());
    assertSame(unexpected, error.found().orElseThrow());
    assertSame(unexpected, cursor.peek().orElseThrow());
  }

  @Test
  void reportsMissingIdentifierAfterDotWithoutConsumingUnexpectedToken() {
    TokenizationResult result = new JackTokenizer().tokenize("foo.(1)");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseTerm());

    assertEquals("token of type IDENTIFIER", error.expected());
    assertSame(result.tokens().get(2), error.found().orElseThrow());
    assertSame(result.tokens().get(2), cursor.peek().orElseThrow());
  }

  @Test
  void rejectsQualifiedCallWithoutOpeningParenthesis() {
    TokenizationResult result = new JackTokenizer().tokenize("Foo.bar;");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseTerm());

    assertEquals("SYMBOL '('", error.expected());
    assertSame(result.tokens().get(3), error.found().orElseThrow());
    assertSame(result.tokens().get(3), cursor.peek().orElseThrow());
  }

  @Test
  void rejectsMissingClosingParenthesisWithoutConsumingFollowingToken() {
    TokenizationResult result = new JackTokenizer().tokenize("foo(1;");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseTerm());

    assertEquals("SYMBOL ')'", error.expected());
    assertSame(result.tokens().get(3), error.found().orElseThrow());
    assertEquals(result.tokens().get(3).position(), error.position());
    assertSame(result.tokens().get(3), cursor.peek().orElseThrow());
  }

  @Test
  void reportsOriginalEofForUnclosedCall() {
    TokenizationResult result = new JackTokenizer().tokenize("foo(1");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseTerm());

    assertEquals("SYMBOL ')'", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(result.endPosition(), error.position());
    assertTrue(cursor.isAtEnd());
  }
}
