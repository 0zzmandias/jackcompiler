package jackcompiler.parser;

import static jackcompiler.parser.ExpressionParserTestSupport.assertGrammar;
import static jackcompiler.parser.ExpressionParserTestSupport.assertTerm;
import static jackcompiler.parser.ExpressionParserTestSupport.assertTerminal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.TokenizationResult;
import org.junit.jupiter.api.Test;

class ExpressionParserArrayAccessTest {

  @Test
  void parsesArrayAccessWithOriginalIdentifierBracketsAndIndexExpression() {
    TokenizationResult result = new JackTokenizer().tokenize("data[i]");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode term = new ExpressionParser(cursor).parseTerm();

    assertEquals(SyntaxTag.TERM, term.tag());
    assertEquals(4, term.children().size());
    assertTerminal(term.children().get(0), result.tokens().get(0));
    assertTerminal(term.children().get(1), result.tokens().get(1));
    GrammarNode index = assertGrammar(term.children().get(2), SyntaxTag.EXPRESSION, 1);
    assertTerm((GrammarNode) index.children().get(0), result.tokens().get(2));
    assertTerminal(term.children().get(3), result.tokens().get(3));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void parsesBinaryExpressionInsideArrayIndex() {
    TokenizationResult result = new JackTokenizer().tokenize("data[i + 2 * j]");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode term = new ExpressionParser(cursor).parseTerm();

    GrammarNode index = assertGrammar(term.children().get(2), SyntaxTag.EXPRESSION, 5);
    assertTerm((GrammarNode) index.children().get(0), result.tokens().get(2));
    assertTerminal(index.children().get(1), result.tokens().get(3));
    assertTerm((GrammarNode) index.children().get(2), result.tokens().get(4));
    assertTerminal(index.children().get(3), result.tokens().get(5));
    assertTerm((GrammarNode) index.children().get(4), result.tokens().get(6));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void parsesNestedArrayAccessWithinAnIndex() {
    TokenizationResult result = new JackTokenizer().tokenize("matrix[indices[k]]");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode term = new ExpressionParser(cursor).parseTerm();

    GrammarNode outerIndex = assertGrammar(term.children().get(2), SyntaxTag.EXPRESSION, 1);
    GrammarNode innerArray = assertGrammar(outerIndex.children().get(0), SyntaxTag.TERM, 4);
    GrammarNode innerIndex = assertGrammar(innerArray.children().get(2), SyntaxTag.EXPRESSION, 1);
    assertTerm((GrammarNode) innerIndex.children().get(0), result.tokens().get(4));
    assertTerminal(innerArray.children().get(3), result.tokens().get(5));
    assertTerminal(term.children().get(3), result.tokens().get(6));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void parsesGroupedAndUnaryTermsInAnArrayIndex() {
    TokenizationResult result = new JackTokenizer().tokenize("data[-(i + 1)]");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode term = new ExpressionParser(cursor).parseTerm();

    GrammarNode index = assertGrammar(term.children().get(2), SyntaxTag.EXPRESSION, 1);
    GrammarNode unary = assertGrammar(index.children().get(0), SyntaxTag.TERM, 2);
    assertTerminal(unary.children().get(0), result.tokens().get(2));
    GrammarNode grouped = assertGrammar(unary.children().get(1), SyntaxTag.TERM, 3);
    GrammarNode groupedExpression =
        assertGrammar(grouped.children().get(1), SyntaxTag.EXPRESSION, 3);
    assertTerminal(groupedExpression.children().get(1), result.tokens().get(5));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void arrayAccessMayBeOperandOfUnaryAndBinaryOperators() {
    TokenizationResult result = new JackTokenizer().tokenize("-a[i] + 1");
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode expression = new ExpressionParser(cursor).parseExpression();

    GrammarNode unary = assertGrammar(expression.children().get(0), SyntaxTag.TERM, 2);
    GrammarNode array = assertGrammar(unary.children().get(1), SyntaxTag.TERM, 4);
    assertTerminal(array.children().get(0), result.tokens().get(1));
    assertTerminal(expression.children().get(1), result.tokens().get(5));
    assertTerm((GrammarNode) expression.children().get(2), result.tokens().get(6));
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void emptyArrayIndexReportsExpectedTermWithoutConsumingClosingBracket() {
    TokenizationResult result = new JackTokenizer().tokenize("a[]");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseTerm());

    assertEquals("term", error.expected());
    assertSame(result.tokens().get(2), error.found().orElseThrow());
    assertSame(result.tokens().get(2), cursor.peek().orElseThrow());
  }

  @Test
  void missingArrayClosingBracketReportsUnexpectedTokenAndLeavesItUnconsumed() {
    TokenizationResult result = new JackTokenizer().tokenize("a[i + 1;");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseTerm());

    assertEquals("SYMBOL ']'", error.expected());
    assertSame(result.tokens().get(5), error.found().orElseThrow());
    assertEquals(result.tokens().get(5).position(), error.position());
    assertSame(result.tokens().get(5), cursor.peek().orElseThrow());
  }

  @Test
  void missingArrayClosingBracketReportsOriginalEofPosition() {
    TokenizationResult result = new JackTokenizer().tokenize("a[2");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseTerm());

    assertEquals("SYMBOL ']'", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(result.endPosition(), error.position());
    assertTrue(cursor.isAtEnd());
  }

  @Test
  void missingArrayIndexTermAtEofReportsOriginalPosition() {
    TokenizationResult result = new JackTokenizer().tokenize("a[");
    TokenCursor cursor = new TokenCursor(result);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new ExpressionParser(cursor).parseTerm());

    assertEquals("term", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(result.endPosition(), error.position());
  }

  @Test
  void integratesRealScannerAcrossWhitespaceAndCommentsWithNestedTerms() {
    String source = "// first\r\n  ~(data[ i + 1 ] = 0) & true // second\n";
    TokenizationResult result = new JackTokenizer().tokenize(source);
    TokenCursor cursor = new TokenCursor(result);

    GrammarNode expression = new ExpressionParser(cursor).parseExpression();

    assertEquals(SyntaxTag.EXPRESSION, expression.tag());
    assertEquals(3, expression.children().size());
    GrammarNode unary = assertGrammar(expression.children().get(0), SyntaxTag.TERM, 2);
    assertTerminal(unary.children().get(0), result.tokens().get(0));
    GrammarNode grouping = assertGrammar(unary.children().get(1), SyntaxTag.TERM, 3);
    GrammarNode inner = assertGrammar(grouping.children().get(1), SyntaxTag.EXPRESSION, 3);
    GrammarNode array = assertGrammar(inner.children().get(0), SyntaxTag.TERM, 4);
    GrammarNode index = assertGrammar(array.children().get(2), SyntaxTag.EXPRESSION, 3);
    assertTerm((GrammarNode) index.children().get(0), result.tokens().get(4));
    assertTerminal(expression.children().get(1), result.tokens().get(11));
    assertTrue(cursor.isAtEnd());
    assertEquals(result.endPosition(), cursor.position());
    assertEquals(new SourcePosition(2, 3), result.tokens().get(0).position());
  }
}
