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

class StatementParserIfTest {

  @Test
  void parsesIfWithEmptyBodyAndNoElse() {
    Fixture f = fixture("if (true) {}");

    GrammarNode statement = f.parser().parseIfStatement();

    assertGrammar(statement, SyntaxTag.IF_STATEMENT, 7);
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
  void parsesIfWithEmptyThenAndElseBlocks() {
    Fixture f = fixture("if (false) {} else {}");

    GrammarNode statement = f.parser().parseIfStatement();

    assertGrammar(statement, SyntaxTag.IF_STATEMENT, 11);
    assertGrammar(statement.children().get(5), SyntaxTag.STATEMENTS, 0);
    assertTerminal(statement.children().get(6), f.result().tokens().get(5));
    assertTerminal(statement.children().get(7), f.result().tokens().get(6));
    assertTerminal(statement.children().get(8), f.result().tokens().get(7));
    assertGrammar(statement.children().get(9), SyntaxTag.STATEMENTS, 0);
    assertTerminal(statement.children().get(10), f.result().tokens().get(8));
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesMultipleStatementsInBothConditionalBranches() {
    Fixture f = fixture("if (x < 10) { let x = x + 1; do tick(); } else { return; }");

    GrammarNode statement = f.parser().parseIfStatement();

    assertGrammar(statement, SyntaxTag.IF_STATEMENT, 11);
    GrammarNode condition = assertGrammar(statement.children().get(2), SyntaxTag.EXPRESSION, 3);
    assertTerminal(condition.children().get(1), f.result().tokens().get(3));

    GrammarNode thenBody = assertGrammar(statement.children().get(5), SyntaxTag.STATEMENTS, 2);
    assertGrammar(thenBody.children().get(0), SyntaxTag.LET_STATEMENT, 5);
    assertGrammar(thenBody.children().get(1), SyntaxTag.DO_STATEMENT, 6);

    GrammarNode elseBody = assertGrammar(statement.children().get(9), SyntaxTag.STATEMENTS, 1);
    assertGrammar(elseBody.children().get(0), SyntaxTag.RETURN_STATEMENT, 2);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesQualifiedSubroutineCallAsCondition() {
    Fixture f = fixture("if (Math.lessThan(a, b)) { return; }");

    GrammarNode statement = f.parser().parseIfStatement();

    GrammarNode condition = assertGrammar(statement.children().get(2), SyntaxTag.EXPRESSION, 1);
    GrammarNode call = assertGrammar(condition.children().get(0), SyntaxTag.TERM, 6);
    assertGrammar(call.children().get(4), SyntaxTag.EXPRESSION_LIST, 3);

    GrammarNode body = assertGrammar(statement.children().get(5), SyntaxTag.STATEMENTS, 1);
    assertGrammar(body.children().get(0), SyntaxTag.RETURN_STATEMENT, 2);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void associatesInnerElseWithNearestIf() {
    Fixture f = fixture("if (a) { if (b) { return; } else { do run(); } }");

    GrammarNode outer = f.parser().parseIfStatement();

    assertGrammar(outer, SyntaxTag.IF_STATEMENT, 7);
    GrammarNode outerBody = assertGrammar(outer.children().get(5), SyntaxTag.STATEMENTS, 1);
    GrammarNode inner = assertGrammar(outerBody.children().get(0), SyntaxTag.IF_STATEMENT, 11);

    GrammarNode innerThen = assertGrammar(inner.children().get(5), SyntaxTag.STATEMENTS, 1);
    assertGrammar(innerThen.children().get(0), SyntaxTag.RETURN_STATEMENT, 2);
    GrammarNode innerElse = assertGrammar(inner.children().get(9), SyntaxTag.STATEMENTS, 1);
    assertGrammar(innerElse.children().get(0), SyntaxTag.DO_STATEMENT, 6);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void associatesElseWithOuterIfWhenInnerIfHasNoElse() {
    Fixture f = fixture("if (a) { if (b) { return; } } else { return; }");

    GrammarNode outer = f.parser().parseIfStatement();

    assertGrammar(outer, SyntaxTag.IF_STATEMENT, 11);
    GrammarNode outerBody = assertGrammar(outer.children().get(5), SyntaxTag.STATEMENTS, 1);
    assertGrammar(outerBody.children().get(0), SyntaxTag.IF_STATEMENT, 7);

    GrammarNode elseBody = assertGrammar(outer.children().get(9), SyntaxTag.STATEMENTS, 1);
    assertGrammar(elseBody.children().get(0), SyntaxTag.RETURN_STATEMENT, 2);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void permitsLiteralConditionWithoutSemanticTypeChecking() {
    Fixture f = fixture("if (1) { return; }");

    GrammarNode statement = f.parser().parseIfStatement();

    assertGrammar(statement, SyntaxTag.IF_STATEMENT, 7);
    GrammarNode condition = assertGrammar(statement.children().get(2), SyntaxTag.EXPRESSION, 1);
    assertGrammar(condition.children().get(0), SyntaxTag.TERM, 1);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void preservesTokenIdentitiesAndPositionsAcrossComments() {
    Fixture f = fixture("// heading\r\n  if (x) {\n    return;\n  } else {\n    do run();\n  }");

    GrammarNode statement = f.parser().parseIfStatement();

    assertGrammar(statement, SyntaxTag.IF_STATEMENT, 11);
    assertTerminal(statement.children().get(0), f.result().tokens().get(0));
    assertEquals(new SourcePosition(2, 3), f.result().tokens().get(0).position());

    GrammarNode thenBody = assertGrammar(statement.children().get(5), SyntaxTag.STATEMENTS, 1);
    GrammarNode returned = assertGrammar(thenBody.children().get(0), SyntaxTag.RETURN_STATEMENT, 2);
    assertTerminal(returned.children().get(0), f.result().tokens().get(5));
    assertEquals(new SourcePosition(3, 5), f.result().tokens().get(5).position());

    GrammarNode elseBody = assertGrammar(statement.children().get(9), SyntaxTag.STATEMENTS, 1);
    GrammarNode call = assertGrammar(elseBody.children().get(0), SyntaxTag.DO_STATEMENT, 6);
    assertTerminal(call.children().get(0), f.result().tokens().get(10));
    assertEquals(new SourcePosition(5, 5), f.result().tokens().get(10).position());
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void leavesFollowingStatementUnconsumed() {
    Fixture f = fixture("if (true) {} return;");

    GrammarNode statement = f.parser().parseIfStatement();

    assertGrammar(statement, SyntaxTag.IF_STATEMENT, 7);
    assertSame(f.result().tokens().get(6), f.cursor().peek().orElseThrow());
    assertEquals("return", f.cursor().peek().orElseThrow().value());
  }

  @Test
  void rejectsWrongStatementKeywordWithoutAdvancing() {
    Fixture f = fixture("while (true) {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseIfStatement());

    assertEquals("KEYWORD 'if'", error.expected());
    assertSame(f.result().tokens().get(0), error.found().orElseThrow());
    assertSame(f.result().tokens().get(0), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresOpeningParenthesisAfterIf() {
    Fixture f = fixture("if true) {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseIfStatement());

    assertEquals("SYMBOL '('", error.expected());
    assertSame(f.result().tokens().get(1), error.found().orElseThrow());
    assertSame(f.result().tokens().get(1), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsEmptyConditionWithoutConsumingClosingParenthesis() {
    Fixture f = fixture("if () {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseIfStatement());

    assertEquals("term", error.expected());
    assertSame(f.result().tokens().get(2), error.found().orElseThrow());
    assertSame(f.result().tokens().get(2), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsIncompleteBinaryExpressionInCondition() {
    Fixture f = fixture("if (x + ) {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseIfStatement());

    assertEquals("term", error.expected());
    assertSame(f.result().tokens().get(4), error.found().orElseThrow());
    assertSame(f.result().tokens().get(4), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresClosingParenthesisAfterCondition() {
    Fixture f = fixture("if (x {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseIfStatement());

    assertEquals("SYMBOL ')'", error.expected());
    assertSame(f.result().tokens().get(3), error.found().orElseThrow());
    assertSame(f.result().tokens().get(3), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresOpeningBraceForThenBlock() {
    Fixture f = fixture("if (x) return;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseIfStatement());

    assertEquals("SYMBOL '{'", error.expected());
    assertSame(f.result().tokens().get(4), error.found().orElseThrow());
    assertSame(f.result().tokens().get(4), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsInvalidStatementInsideThenBlock() {
    Fixture f = fixture("if (true) { var int x; }");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseIfStatement());

    assertEquals("statement (let, if, while, do or return)", error.expected());
    assertSame(f.result().tokens().get(5), error.found().orElseThrow());
    assertSame(f.result().tokens().get(5), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresClosingThenBraceBeforeElse() {
    Fixture f = fixture("if (true) { return; else { return; }");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseIfStatement());

    assertEquals("statement (let, if, while, do or return)", error.expected());
    assertSame(f.result().tokens().get(7), error.found().orElseThrow());
    assertSame(f.result().tokens().get(7), f.cursor().peek().orElseThrow());
  }

  @Test
  void reportsOriginalEofWhenThenBlockIsUnclosed() {
    Fixture f = fixture("if (true) { return;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseIfStatement());

    assertEquals("SYMBOL '}'", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(f.result().endPosition(), error.position());
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void requiresOpeningBraceAfterElse() {
    Fixture f = fixture("if (true) {} else return;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseIfStatement());

    assertEquals("SYMBOL '{'", error.expected());
    assertSame(f.result().tokens().get(7), error.found().orElseThrow());
    assertSame(f.result().tokens().get(7), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresClosingBraceForElseBlockAtEof() {
    Fixture f = fixture("if (true) {} else { do run();");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseIfStatement());

    assertEquals("SYMBOL '}'", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(f.result().endPosition(), error.position());
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void rejectsInvalidStatementInsideElseBlock() {
    Fixture f = fixture("if (true) {} else { var int x; }");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseIfStatement());

    assertEquals("statement (let, if, while, do or return)", error.expected());
    assertSame(f.result().tokens().get(8), error.found().orElseThrow());
    assertSame(f.result().tokens().get(8), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresBracesInsteadOfAcceptingUnbracedElseIf() {
    Fixture f = fixture("if (true) {} else if (false) {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseIfStatement());

    assertEquals("SYMBOL '{'", error.expected());
    Token unexpected = f.result().tokens().get(7);
    assertSame(unexpected, error.found().orElseThrow());
    assertEquals(unexpected.position(), error.position());
    assertSame(unexpected, f.cursor().peek().orElseThrow());
  }
}
