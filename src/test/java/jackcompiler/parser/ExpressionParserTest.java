package jackcompiler.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.token.TokenizationResult;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExpressionParserTest {

  @Nested
  class AtomicTermsAndBinaryExpressions {

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
      TokenizationResult result =
          new TokenizationResult(List.of(a, op, b), new SourcePosition(1, 9));
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

  @Nested
  class ParenthesizedAndUnaryTerms {

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

  @Nested
  class ArrayAccessTerms {

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

  private static GrammarNode assertGrammar(ParseNode node, SyntaxTag tag, int childCount) {
    assertTrue(node instanceof GrammarNode);
    GrammarNode grammar = (GrammarNode) node;
    assertEquals(tag, grammar.tag());
    assertEquals(childCount, grammar.children().size());
    return grammar;
  }

  private static void assertTerminal(ParseNode node, Token expectedToken) {
    assertTrue(node instanceof TerminalNode);
    assertSame(expectedToken, ((TerminalNode) node).token());
  }

  private static void assertTerm(GrammarNode term, Token expectedToken) {
    assertEquals(SyntaxTag.TERM, term.tag());
    assertEquals(1, term.children().size());
    ParseNode child = term.children().get(0);
    assertTrue(child instanceof TerminalNode);
    assertSame(expectedToken, ((TerminalNode) child).token());
  }

  private static TokenCursor cursor(Token... tokens) {
    List<Token> original = List.of(tokens);
    SourcePosition eof =
        original.isEmpty()
            ? new SourcePosition(1, 1)
            : new SourcePosition(original.get(original.size() - 1).position().line(), 100);
    return new TokenCursor(new TokenizationResult(original, eof));
  }

  private static Token token(TokenType type, String value, int line, int column) {
    return new Token(type, value, new SourcePosition(line, column));
  }
}
