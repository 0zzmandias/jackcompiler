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

class StatementParserReturnTest {

  @Test
  void parsesReturnWithoutExpression() {
    Fixture f = fixture("return;");

    GrammarNode statement = f.parser().parseReturnStatement();

    assertGrammar(statement, SyntaxTag.RETURN_STATEMENT, 2);
    assertTerminal(statement.children().get(0), f.result().tokens().get(0));
    assertTerminal(statement.children().get(1), f.result().tokens().get(1));
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesReturnWithIntegerExpression() {
    Fixture f = fixture("return 0;");

    GrammarNode statement = f.parser().parseReturnStatement();

    assertGrammar(statement, SyntaxTag.RETURN_STATEMENT, 3);
    assertTerminal(statement.children().get(0), f.result().tokens().get(0));
    GrammarNode expression = assertGrammar(statement.children().get(1), SyntaxTag.EXPRESSION, 1);
    GrammarNode term = assertGrammar(expression.children().get(0), SyntaxTag.TERM, 1);
    assertTerminal(term.children().get(0), f.result().tokens().get(1));
    assertTerminal(statement.children().get(2), f.result().tokens().get(2));
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void acceptsAllFourKeywordConstantsAsReturnExpressions() {
    for (String keyword : java.util.List.of("true", "false", "null", "this")) {
      Fixture f = fixture("return " + keyword + ";");

      GrammarNode statement = f.parser().parseReturnStatement();

      assertGrammar(statement, SyntaxTag.RETURN_STATEMENT, 3);
      GrammarNode expression = assertGrammar(statement.children().get(1), SyntaxTag.EXPRESSION, 1);
      GrammarNode term = assertGrammar(expression.children().get(0), SyntaxTag.TERM, 1);
      assertTerminal(term.children().get(0), f.result().tokens().get(1));
      assertTrue(f.cursor().isAtEnd());
    }
  }

  @Test
  void parsesGroupedUnaryAndBinaryReturnExpression() {
    Fixture f = fixture("return -(a + b);");

    GrammarNode statement = f.parser().parseReturnStatement();

    GrammarNode expression = assertGrammar(statement.children().get(1), SyntaxTag.EXPRESSION, 1);
    GrammarNode unary = assertGrammar(expression.children().get(0), SyntaxTag.TERM, 2);
    assertTerminal(unary.children().get(0), f.result().tokens().get(1));
    GrammarNode grouped = assertGrammar(unary.children().get(1), SyntaxTag.TERM, 3);
    assertGrammar(grouped.children().get(1), SyntaxTag.EXPRESSION, 3);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesQualifiedSubroutineCallAsReturnValue() {
    Fixture f = fixture("return Math.multiply(a, b);");

    GrammarNode statement = f.parser().parseReturnStatement();

    GrammarNode expression = assertGrammar(statement.children().get(1), SyntaxTag.EXPRESSION, 1);
    GrammarNode call = assertGrammar(expression.children().get(0), SyntaxTag.TERM, 6);
    assertTerminal(call.children().get(0), f.result().tokens().get(1));
    assertTerminal(call.children().get(1), f.result().tokens().get(2));
    assertTerminal(call.children().get(2), f.result().tokens().get(3));
    assertGrammar(call.children().get(4), SyntaxTag.EXPRESSION_LIST, 3);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void preservesWhitespaceAndSpecialCharactersInReturnedString() {
    Fixture f = fixture("return \" a & < b \";");

    GrammarNode statement = f.parser().parseReturnStatement();

    GrammarNode expression = assertGrammar(statement.children().get(1), SyntaxTag.EXPRESSION, 1);
    GrammarNode term = assertGrammar(expression.children().get(0), SyntaxTag.TERM, 1);
    Token original = f.result().tokens().get(1);
    assertEquals(" a & < b ", original.value());
    assertTerminal(term.children().get(0), original);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void leavesEnclosingClosingBraceUnconsumed() {
    Fixture f = fixture("return;}");

    GrammarNode statement = f.parser().parseReturnStatement();

    assertGrammar(statement, SyntaxTag.RETURN_STATEMENT, 2);
    assertSame(f.result().tokens().get(2), f.cursor().peek().orElseThrow());
    assertEquals("}", f.cursor().peek().orElseThrow().value());
  }

  @Test
  void rejectsWrongStatementKeywordWithoutAdvancing() {
    Fixture f = fixture("do f();");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseReturnStatement());

    assertEquals("KEYWORD 'return'", error.expected());
    assertSame(f.result().tokens().get(0), error.found().orElseThrow());
    assertSame(f.result().tokens().get(0), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsUnaryOperatorWithoutOperand() {
    Fixture f = fixture("return -;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseReturnStatement());

    assertEquals("term", error.expected());
    assertSame(f.result().tokens().get(2), error.found().orElseThrow());
    assertSame(f.result().tokens().get(2), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsInvalidTokenAtBeginningOfReturnExpression() {
    Fixture f = fixture("return ,;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseReturnStatement());

    assertEquals("term", error.expected());
    assertSame(f.result().tokens().get(1), error.found().orElseThrow());
    assertSame(f.result().tokens().get(1), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsMissingSemicolonBeforeClosingBrace() {
    Fixture f = fixture("return x }");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseReturnStatement());

    assertEquals("SYMBOL ';'", error.expected());
    assertSame(f.result().tokens().get(2), error.found().orElseThrow());
    assertEquals(f.result().tokens().get(2).position(), error.position());
    assertSame(f.result().tokens().get(2), f.cursor().peek().orElseThrow());
  }

  @Test
  void reportsMissingSemicolonAtOriginalEof() {
    Fixture f = fixture("return x");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseReturnStatement());

    assertEquals("SYMBOL ';'", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(f.result().endPosition(), error.position());
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void stopsBeforeFollowingReturnStatement() {
    Fixture f = fixture("return; return 1;");

    GrammarNode statement = f.parser().parseReturnStatement();

    assertGrammar(statement, SyntaxTag.RETURN_STATEMENT, 2);
    assertSame(f.result().tokens().get(2), f.cursor().peek().orElseThrow());
    assertEquals("return", f.cursor().peek().orElseThrow().value());
  }
}
