package jackcompiler.parser;

import static jackcompiler.parser.StatementParserTestSupport.assertGrammar;
import static jackcompiler.parser.StatementParserTestSupport.assertTerminal;
import static jackcompiler.parser.StatementParserTestSupport.fixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.parser.StatementParserTestSupport.Fixture;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.token.Token;
import org.junit.jupiter.api.Test;

class StatementParserLetTest {

  @Test
  void parsesSimpleAssignmentWithAllOriginalTerminals() {
    Fixture f = fixture("let count = 3;");

    GrammarNode statement = f.parser().parseLetStatement();

    assertGrammar(statement, SyntaxTag.LET_STATEMENT, 5);
    assertTerminal(statement.children().get(0), f.result().tokens().get(0));
    assertTerminal(statement.children().get(1), f.result().tokens().get(1));
    assertTerminal(statement.children().get(2), f.result().tokens().get(2));
    GrammarNode value = assertGrammar(statement.children().get(3), SyntaxTag.EXPRESSION, 1);
    GrammarNode term = assertGrammar(value.children().get(0), SyntaxTag.TERM, 1);
    assertTerminal(term.children().get(0), f.result().tokens().get(3));
    assertTerminal(statement.children().get(4), f.result().tokens().get(4));
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesArrayElementAssignmentWithIndexExpression() {
    Fixture f = fixture("let data[i + 1] = 42;");

    GrammarNode statement = f.parser().parseLetStatement();

    assertGrammar(statement, SyntaxTag.LET_STATEMENT, 8);
    assertTerminal(statement.children().get(0), f.result().tokens().get(0));
    assertTerminal(statement.children().get(1), f.result().tokens().get(1));
    assertTerminal(statement.children().get(2), f.result().tokens().get(2));
    GrammarNode index = assertGrammar(statement.children().get(3), SyntaxTag.EXPRESSION, 3);
    assertTerminal(index.children().get(1), f.result().tokens().get(4));
    assertTerminal(statement.children().get(4), f.result().tokens().get(6));
    assertTerminal(statement.children().get(5), f.result().tokens().get(7));
    assertGrammar(statement.children().get(6), SyntaxTag.EXPRESSION, 1);
    assertTerminal(statement.children().get(7), f.result().tokens().get(9));
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void supportsNestedArraysAndSubroutineCallsInAssignment() {
    Fixture f = fixture("let matrix[indices[k]] = values[f(x)];");

    GrammarNode statement = f.parser().parseLetStatement();

    assertGrammar(statement, SyntaxTag.LET_STATEMENT, 8);
    GrammarNode index = assertGrammar(statement.children().get(3), SyntaxTag.EXPRESSION, 1);
    GrammarNode nestedArray = assertGrammar(index.children().get(0), SyntaxTag.TERM, 4);
    assertGrammar(nestedArray.children().get(2), SyntaxTag.EXPRESSION, 1);

    GrammarNode value = assertGrammar(statement.children().get(6), SyntaxTag.EXPRESSION, 1);
    GrammarNode valueArray = assertGrammar(value.children().get(0), SyntaxTag.TERM, 4);
    GrammarNode callExpression =
        assertGrammar(valueArray.children().get(2), SyntaxTag.EXPRESSION, 1);
    GrammarNode call = assertGrammar(callExpression.children().get(0), SyntaxTag.TERM, 4);
    assertGrammar(call.children().get(2), SyntaxTag.EXPRESSION_LIST, 1);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void supportsGroupedUnaryAndBinaryExpressionsAsAssignedValues() {
    Fixture f = fixture("let answer = -(a + 2) * 3;");

    GrammarNode statement = f.parser().parseLetStatement();

    GrammarNode value = assertGrammar(statement.children().get(3), SyntaxTag.EXPRESSION, 3);
    GrammarNode unary = assertGrammar(value.children().get(0), SyntaxTag.TERM, 2);
    GrammarNode grouped = assertGrammar(unary.children().get(1), SyntaxTag.TERM, 3);
    assertGrammar(grouped.children().get(1), SyntaxTag.EXPRESSION, 3);
    assertTerminal(value.children().get(1), f.result().tokens().get(9));
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void preservesStringContentsAndOriginalTokens() {
    Fixture f = fixture("let message = \" a & b \";");

    GrammarNode statement = f.parser().parseLetStatement();

    GrammarNode expression = assertGrammar(statement.children().get(3), SyntaxTag.EXPRESSION, 1);
    GrammarNode term = assertGrammar(expression.children().get(0), SyntaxTag.TERM, 1);
    Token original = f.result().tokens().get(3);
    assertEquals(" a & b ", original.value());
    assertTerminal(term.children().get(0), original);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void rejectsWrongStatementKeywordWithoutConsumingIt() {
    Fixture f = fixture("do run();");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseLetStatement());

    assertEquals("KEYWORD 'let'", error.expected());
    assertSame(f.result().tokens().get(0), error.found().orElseThrow());
    assertSame(f.result().tokens().get(0), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresIdentifierAfterLet() {
    Fixture f = fixture("let = 1;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseLetStatement());

    assertEquals("token of type IDENTIFIER", error.expected());
    assertSame(f.result().tokens().get(1), error.found().orElseThrow());
    assertSame(f.result().tokens().get(1), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsEmptyArrayIndexWithoutConsumingClosingBracket() {
    Fixture f = fixture("let a[] = 1;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseLetStatement());

    assertEquals("term", error.expected());
    assertSame(f.result().tokens().get(3), error.found().orElseThrow());
    assertSame(f.result().tokens().get(3), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresClosingArrayBracketAfterIndexExpression() {
    Fixture f = fixture("let a[i + 1;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseLetStatement());

    Token unexpected = f.result().tokens().get(6);
    assertEquals("SYMBOL ']'", error.expected());
    assertSame(unexpected, error.found().orElseThrow());
    assertEquals(unexpected.position(), error.position());
    assertSame(unexpected, f.cursor().peek().orElseThrow());
  }

  @Test
  void reportsOriginalEofWhenArrayIndexIsNotClosed() {
    Fixture f = fixture("let a[i");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseLetStatement());

    assertEquals("SYMBOL ']'", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(f.result().endPosition(), error.position());
  }

  @Test
  void requiresAssignmentOperatorAfterVariableName() {
    Fixture f = fixture("let x 2;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseLetStatement());

    assertEquals("SYMBOL '='", error.expected());
    assertSame(f.result().tokens().get(2), error.found().orElseThrow());
    assertSame(f.result().tokens().get(2), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsMissingAssignedExpression() {
    Fixture f = fixture("let x = ;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseLetStatement());

    assertEquals("term", error.expected());
    assertSame(f.result().tokens().get(3), error.found().orElseThrow());
    assertSame(f.result().tokens().get(3), f.cursor().peek().orElseThrow());
  }

  @Test
  void reportsMissingSemicolonAtEof() {
    Fixture f = fixture("let x = 1");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseLetStatement());

    assertEquals("SYMBOL ';'", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(f.result().endPosition(), error.position());
  }

  @Test
  void rejectsClosingBraceWhereSemicolonIsRequired() {
    Fixture f = fixture("let x = 1 }");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseLetStatement());

    assertEquals("SYMBOL ';'", error.expected());
    assertSame(f.result().tokens().get(4), error.found().orElseThrow());
    assertSame(f.result().tokens().get(4), f.cursor().peek().orElseThrow());
  }

  @Test
  void stopsImmediatelyAfterSemicolonWithoutConsumingNextStatement() {
    Fixture f = fixture("let x = 1; return;");

    GrammarNode statement = f.parser().parseLetStatement();

    assertGrammar(statement, SyntaxTag.LET_STATEMENT, 5);
    assertSame(f.result().tokens().get(5), f.cursor().peek().orElseThrow());
    assertEquals("return", f.cursor().peek().orElseThrow().value());
  }

  @Test
  void rejectsIncompleteBinaryExpressionBeforeSemicolon() {
    Fixture f = fixture("let x = 1 + ;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseLetStatement());

    assertEquals("term", error.expected());
    assertSame(f.result().tokens().get(5), error.found().orElseThrow());
    assertSame(f.result().tokens().get(5), f.cursor().peek().orElseThrow());
  }
}
