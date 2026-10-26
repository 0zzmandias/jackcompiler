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
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import org.junit.jupiter.api.Test;

class StatementParserWhileTest {

  @Test
  void parsesWhileWithEmptyBody() {
    Fixture f = fixture("while (true) {}");

    GrammarNode statement = f.parser().parseWhileStatement();

    assertGrammar(statement, SyntaxTag.WHILE_STATEMENT, 7);
    assertTerminal(statement.children().get(0), f.result().tokens().get(0));
    assertTerminal(statement.children().get(1), f.result().tokens().get(1));
    assertGrammar(statement.children().get(2), SyntaxTag.EXPRESSION, 1);
    assertTerminal(statement.children().get(3), f.result().tokens().get(3));
    assertTerminal(statement.children().get(4), f.result().tokens().get(4));
    assertGrammar(statement.children().get(5), SyntaxTag.STATEMENTS, 0);
    assertTerminal(statement.children().get(6), f.result().tokens().get(5));
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesMultipleStatementsInsideWhileBody() {
    Fixture f = fixture("while (x) { let x = x - 1; do tick(); return; }");

    GrammarNode statement = f.parser().parseWhileStatement();

    assertGrammar(statement, SyntaxTag.WHILE_STATEMENT, 7);
    GrammarNode body = assertGrammar(statement.children().get(5), SyntaxTag.STATEMENTS, 3);
    assertGrammar(body.children().get(0), SyntaxTag.LET_STATEMENT, 5);
    assertGrammar(body.children().get(1), SyntaxTag.DO_STATEMENT, 6);
    assertGrammar(body.children().get(2), SyntaxTag.RETURN_STATEMENT, 2);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesRecursivelyNestedWhileStatements() {
    Fixture f = fixture("while (x) { while (y) { do tick(); } }");

    GrammarNode outer = f.parser().parseWhileStatement();

    assertGrammar(outer, SyntaxTag.WHILE_STATEMENT, 7);
    GrammarNode outerBody = assertGrammar(outer.children().get(5), SyntaxTag.STATEMENTS, 1);
    GrammarNode inner = assertGrammar(outerBody.children().get(0), SyntaxTag.WHILE_STATEMENT, 7);
    GrammarNode innerBody = assertGrammar(inner.children().get(5), SyntaxTag.STATEMENTS, 1);
    assertGrammar(innerBody.children().get(0), SyntaxTag.DO_STATEMENT, 6);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesIfElseInsideWhileBody() {
    Fixture f = fixture("while (x) { if (x) { return; } else { do tick(); } }");

    GrammarNode loop = f.parser().parseWhileStatement();

    GrammarNode body = assertGrammar(loop.children().get(5), SyntaxTag.STATEMENTS, 1);
    GrammarNode conditional = assertGrammar(body.children().get(0), SyntaxTag.IF_STATEMENT, 11);
    GrammarNode thenBody = assertGrammar(conditional.children().get(5), SyntaxTag.STATEMENTS, 1);
    GrammarNode elseBody = assertGrammar(conditional.children().get(9), SyntaxTag.STATEMENTS, 1);
    assertGrammar(thenBody.children().get(0), SyntaxTag.RETURN_STATEMENT, 2);
    assertGrammar(elseBody.children().get(0), SyntaxTag.DO_STATEMENT, 6);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesGroupedUnaryCondition() {
    Fixture f = fixture("while (~(x = 0)) {}");

    GrammarNode statement = f.parser().parseWhileStatement();

    GrammarNode condition = assertGrammar(statement.children().get(2), SyntaxTag.EXPRESSION, 1);
    GrammarNode unary = assertGrammar(condition.children().get(0), SyntaxTag.TERM, 2);
    assertTerminal(unary.children().get(0), f.result().tokens().get(2));
    GrammarNode grouping = assertGrammar(unary.children().get(1), SyntaxTag.TERM, 3);
    assertGrammar(grouping.children().get(1), SyntaxTag.EXPRESSION, 3);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesQualifiedCallAndBinaryOperatorInCondition() {
    Fixture f = fixture("while (Math.lessThan(a, b) & ready) {}");

    GrammarNode statement = f.parser().parseWhileStatement();

    GrammarNode condition = assertGrammar(statement.children().get(2), SyntaxTag.EXPRESSION, 3);
    GrammarNode call = assertGrammar(condition.children().get(0), SyntaxTag.TERM, 6);
    assertGrammar(call.children().get(4), SyntaxTag.EXPRESSION_LIST, 3);
    assertTerminal(condition.children().get(1), f.result().tokens().get(10));
    assertGrammar(condition.children().get(2), SyntaxTag.TERM, 1);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void permitsLiteralConditionWithoutPerformingSemanticAnalysis() {
    Fixture f = fixture("while (1) {}");

    GrammarNode statement = f.parser().parseWhileStatement();

    assertGrammar(statement, SyntaxTag.WHILE_STATEMENT, 7);
    GrammarNode condition = assertGrammar(statement.children().get(2), SyntaxTag.EXPRESSION, 1);
    assertGrammar(condition.children().get(0), SyntaxTag.TERM, 1);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void preservesOriginalTokensAndPositionsAcrossComments() {
    Fixture f = fixture("// heading\r\n while (x) {\n  return;\n }");

    GrammarNode statement = f.parser().parseWhileStatement();

    assertGrammar(statement, SyntaxTag.WHILE_STATEMENT, 7);
    assertTerminal(statement.children().get(0), f.result().tokens().get(0));
    assertEquals(new SourcePosition(2, 2), f.result().tokens().get(0).position());

    GrammarNode body = assertGrammar(statement.children().get(5), SyntaxTag.STATEMENTS, 1);
    GrammarNode returned = assertGrammar(body.children().get(0), SyntaxTag.RETURN_STATEMENT, 2);
    assertTerminal(returned.children().get(0), f.result().tokens().get(5));
    assertEquals(new SourcePosition(3, 3), f.result().tokens().get(5).position());

    assertTerminal(
        statement.children().get(6), f.result().tokens().get(f.result().tokens().size() - 1));
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void leavesFollowingStatementUnconsumed() {
    Fixture f = fixture("while (x) {} return;");

    GrammarNode statement = f.parser().parseWhileStatement();

    assertGrammar(statement, SyntaxTag.WHILE_STATEMENT, 7);
    assertSame(f.result().tokens().get(6), f.cursor().peek().orElseThrow());
    assertEquals("return", f.cursor().peek().orElseThrow().value());
  }

  @Test
  void doesNotTreatElseAsPartOfWhileStatement() {
    Fixture f = fixture("while (x) {} else {}");

    GrammarNode statement = f.parser().parseWhileStatement();

    assertGrammar(statement, SyntaxTag.WHILE_STATEMENT, 7);
    assertSame(f.result().tokens().get(6), f.cursor().peek().orElseThrow());
    assertEquals("else", f.cursor().peek().orElseThrow().value());
  }

  @Test
  void rejectsWrongStatementKeywordWithoutAdvancing() {
    Fixture f = fixture("if (true) {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseWhileStatement());

    assertEquals("KEYWORD 'while'", error.expected());
    assertSame(f.result().tokens().get(0), error.found().orElseThrow());
    assertSame(f.result().tokens().get(0), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresOpeningParenthesisAfterWhile() {
    Fixture f = fixture("while true) {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseWhileStatement());

    assertEquals("SYMBOL '('", error.expected());
    assertSame(f.result().tokens().get(1), error.found().orElseThrow());
    assertSame(f.result().tokens().get(1), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsEmptyConditionWithoutConsumingClosingParenthesis() {
    Fixture f = fixture("while () {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseWhileStatement());

    assertEquals("term", error.expected());
    assertSame(f.result().tokens().get(2), error.found().orElseThrow());
    assertSame(f.result().tokens().get(2), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsIncompleteBinaryExpressionInCondition() {
    Fixture f = fixture("while (x + ) {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseWhileStatement());

    assertEquals("term", error.expected());
    assertSame(f.result().tokens().get(4), error.found().orElseThrow());
    assertSame(f.result().tokens().get(4), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresClosingParenthesisAfterCondition() {
    Fixture f = fixture("while (x {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseWhileStatement());

    assertEquals("SYMBOL ')'", error.expected());
    assertSame(f.result().tokens().get(3), error.found().orElseThrow());
    assertSame(f.result().tokens().get(3), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresOpeningBraceForWhileBody() {
    Fixture f = fixture("while (x) return;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseWhileStatement());

    assertEquals("SYMBOL '{'", error.expected());
    assertSame(f.result().tokens().get(4), error.found().orElseThrow());
    assertSame(f.result().tokens().get(4), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsInvalidStatementInsideWhileBody() {
    Fixture f = fixture("while (x) { var int y; }");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseWhileStatement());

    assertEquals("statement (let, if, while, do or return)", error.expected());
    assertSame(f.result().tokens().get(5), error.found().orElseThrow());
    assertSame(f.result().tokens().get(5), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsUnexpectedDelimiterInsideWhileBody() {
    Fixture f = fixture("while (x) { return; )");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseWhileStatement());

    assertEquals("statement (let, if, while, do or return)", error.expected());
    assertSame(f.result().tokens().get(7), error.found().orElseThrow());
    assertSame(f.result().tokens().get(7), f.cursor().peek().orElseThrow());
  }

  @Test
  void reportsOriginalEofWhenWhileBodyIsUnclosed() {
    Fixture f = fixture("while (x) { return;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseWhileStatement());

    assertEquals("SYMBOL '}'", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(f.result().endPosition(), error.position());
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void propagatesSyntaxErrorFromNestedIfWithoutLosingPosition() {
    Fixture f = fixture("while (x) { if (x { return; } }");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseWhileStatement());

    assertEquals("SYMBOL ')'", error.expected());
    Token unexpected = f.cursor().peek().orElseThrow();
    assertEquals("{", unexpected.value());
    assertSame(unexpected, error.found().orElseThrow());
    assertEquals(unexpected.position(), error.position());
  }
}
