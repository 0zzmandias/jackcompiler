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

class StatementParserDoTest {

  @Test
  void parsesDirectCallWithoutArguments() {
    Fixture f = fixture("do run();");

    GrammarNode statement = f.parser().parseDoStatement();

    assertGrammar(statement, SyntaxTag.DO_STATEMENT, 6);
    assertTerminal(statement.children().get(0), f.result().tokens().get(0));
    assertTerminal(statement.children().get(1), f.result().tokens().get(1));
    assertTerminal(statement.children().get(2), f.result().tokens().get(2));
    assertGrammar(statement.children().get(3), SyntaxTag.EXPRESSION_LIST, 0);
    assertTerminal(statement.children().get(4), f.result().tokens().get(3));
    assertTerminal(statement.children().get(5), f.result().tokens().get(4));
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesQualifiedCallWithMultipleArguments() {
    Fixture f = fixture("do Output.printInt(x, 2);");

    GrammarNode statement = f.parser().parseDoStatement();

    assertGrammar(statement, SyntaxTag.DO_STATEMENT, 8);
    assertTerminal(statement.children().get(0), f.result().tokens().get(0));
    assertTerminal(statement.children().get(1), f.result().tokens().get(1));
    assertTerminal(statement.children().get(2), f.result().tokens().get(2));
    assertTerminal(statement.children().get(3), f.result().tokens().get(3));
    assertTerminal(statement.children().get(4), f.result().tokens().get(4));
    GrammarNode arguments =
        assertGrammar(statement.children().get(5), SyntaxTag.EXPRESSION_LIST, 3);
    assertGrammar(arguments.children().get(0), SyntaxTag.EXPRESSION, 1);
    assertTerminal(arguments.children().get(1), f.result().tokens().get(6));
    assertGrammar(arguments.children().get(2), SyntaxTag.EXPRESSION, 1);
    assertTerminal(statement.children().get(6), f.result().tokens().get(8));
    assertTerminal(statement.children().get(7), f.result().tokens().get(9));
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesCallsNestedInsideArgumentExpressions() {
    Fixture f = fixture("do f(g(a), h());");

    GrammarNode statement = f.parser().parseDoStatement();

    assertGrammar(statement, SyntaxTag.DO_STATEMENT, 6);
    GrammarNode arguments =
        assertGrammar(statement.children().get(3), SyntaxTag.EXPRESSION_LIST, 3);
    GrammarNode firstExpression =
        assertGrammar(arguments.children().get(0), SyntaxTag.EXPRESSION, 1);
    GrammarNode firstCall = assertGrammar(firstExpression.children().get(0), SyntaxTag.TERM, 4);
    assertGrammar(firstCall.children().get(2), SyntaxTag.EXPRESSION_LIST, 1);
    assertTerminal(arguments.children().get(1), f.result().tokens().get(7));
    GrammarNode secondExpression =
        assertGrammar(arguments.children().get(2), SyntaxTag.EXPRESSION, 1);
    GrammarNode secondCall = assertGrammar(secondExpression.children().get(0), SyntaxTag.TERM, 4);
    assertGrammar(secondCall.children().get(2), SyntaxTag.EXPRESSION_LIST, 0);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void preservesWhitespaceAndCommaInsideStringArgument() {
    Fixture f = fixture("do Output.printString(\" a,b \");");

    GrammarNode statement = f.parser().parseDoStatement();

    GrammarNode arguments =
        assertGrammar(statement.children().get(5), SyntaxTag.EXPRESSION_LIST, 1);
    GrammarNode expression = assertGrammar(arguments.children().get(0), SyntaxTag.EXPRESSION, 1);
    GrammarNode term = assertGrammar(expression.children().get(0), SyntaxTag.TERM, 1);
    Token original = f.result().tokens().get(5);
    assertEquals(" a,b ", original.value());
    assertTerminal(term.children().get(0), original);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void doesNotConsumeNextStatementAfterSemicolon() {
    Fixture f = fixture("do f(); let x = 1;");

    GrammarNode statement = f.parser().parseDoStatement();

    assertGrammar(statement, SyntaxTag.DO_STATEMENT, 6);
    assertSame(f.result().tokens().get(5), f.cursor().peek().orElseThrow());
    assertEquals("let", f.cursor().peek().orElseThrow().value());
  }

  @Test
  void rejectsWrongStatementKeyword() {
    Fixture f = fixture("return;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseDoStatement());

    assertEquals("KEYWORD 'do'", error.expected());
    assertSame(f.result().tokens().get(0), error.found().orElseThrow());
    assertSame(f.result().tokens().get(0), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresSubroutineIdentifierAfterDo() {
    Fixture f = fixture("do (1);");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseDoStatement());

    assertEquals("token of type IDENTIFIER", error.expected());
    assertSame(f.result().tokens().get(1), error.found().orElseThrow());
    assertSame(f.result().tokens().get(1), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresIdentifierAfterQualifiedCallDot() {
    Fixture f = fixture("do Foo.(2);");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseDoStatement());

    assertEquals("token of type IDENTIFIER", error.expected());
    assertSame(f.result().tokens().get(3), error.found().orElseThrow());
    assertSame(f.result().tokens().get(3), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresOpeningParenthesisAfterSubroutineName() {
    Fixture f = fixture("do Foo.bar;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseDoStatement());

    assertEquals("SYMBOL '('", error.expected());
    assertSame(f.result().tokens().get(4), error.found().orElseThrow());
    assertSame(f.result().tokens().get(4), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsLeadingCommaInsideArgumentList() {
    Fixture f = fixture("do f(,);");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseDoStatement());

    assertEquals("term", error.expected());
    assertSame(f.result().tokens().get(3), error.found().orElseThrow());
    assertSame(f.result().tokens().get(3), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsTrailingCommaInsideArgumentList() {
    Fixture f = fixture("do f(x,);");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseDoStatement());

    assertEquals("term", error.expected());
    assertSame(f.result().tokens().get(5), error.found().orElseThrow());
    assertSame(f.result().tokens().get(5), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresClosingParenthesisBeforeSemicolon() {
    Fixture f = fixture("do f(x;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseDoStatement());

    assertEquals("SYMBOL ')'", error.expected());
    assertSame(f.result().tokens().get(4), error.found().orElseThrow());
    assertEquals(f.result().tokens().get(4).position(), error.position());
    assertSame(f.result().tokens().get(4), f.cursor().peek().orElseThrow());
  }

  @Test
  void reportsMissingSemicolonAtEof() {
    Fixture f = fixture("do f()");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseDoStatement());

    assertEquals("SYMBOL ';'", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(f.result().endPosition(), error.position());
  }

  @Test
  void rejectsClosingBraceWhereSemicolonIsRequired() {
    Fixture f = fixture("do f()}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseDoStatement());

    assertEquals("SYMBOL ';'", error.expected());
    assertSame(f.result().tokens().get(4), error.found().orElseThrow());
    assertSame(f.result().tokens().get(4), f.cursor().peek().orElseThrow());
  }
}
