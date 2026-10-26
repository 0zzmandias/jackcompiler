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
import java.util.List;
import org.junit.jupiter.api.Test;

class StatementParserTest {

  @Test
  void rejectsNullCursor() {
    Fixture f = fixture("");

    assertThrows(
        NullPointerException.class,
        () -> new StatementParser(null, new ExpressionParser(f.cursor())));
  }

  @Test
  void rejectsNullExpressionParser() {
    Fixture f = fixture("");

    assertThrows(NullPointerException.class, () -> new StatementParser(f.cursor(), null));
  }

  @Test
  void parsesEmptyStatementsBeforeClosingBraceWithoutConsumingIt() {
    Fixture f = fixture("}");

    GrammarNode statements = f.parser().parseStatements();

    assertGrammar(statements, SyntaxTag.STATEMENTS, 0);
    assertSame(f.result().tokens().get(0), f.cursor().peek().orElseThrow());
  }

  @Test
  void permitsEmptyStatementSequenceAtEof() {
    Fixture f = fixture("");

    GrammarNode statements = f.parser().parseStatements();

    assertGrammar(statements, SyntaxTag.STATEMENTS, 0);
    assertTrue(f.cursor().isAtEnd());
    assertEquals(f.result().endPosition(), f.cursor().position());
  }

  @Test
  void parsesMixedStatementsInSourceOrder() {
    Fixture f = fixture("let x = 1; do Output.printInt(x); return; }");

    GrammarNode statements = f.parser().parseStatements();

    assertGrammar(statements, SyntaxTag.STATEMENTS, 3);
    assertGrammar(statements.children().get(0), SyntaxTag.LET_STATEMENT, 5);
    assertGrammar(statements.children().get(1), SyntaxTag.DO_STATEMENT, 8);
    assertGrammar(statements.children().get(2), SyntaxTag.RETURN_STATEMENT, 2);
    assertSame(
        f.result().tokens().get(f.result().tokens().size() - 1), f.cursor().peek().orElseThrow());
  }

  @Test
  void integratesScannerAcrossCommentsAndPreservesSourcePositions() {
    Fixture f = fixture("// heading\r\n let x = 1; // middle\n return; }");

    GrammarNode statements = f.parser().parseStatements();

    assertGrammar(statements, SyntaxTag.STATEMENTS, 2);
    GrammarNode first = assertGrammar(statements.children().get(0), SyntaxTag.LET_STATEMENT, 5);
    assertTerminal(first.children().get(0), f.result().tokens().get(0));
    assertEquals(new SourcePosition(2, 2), f.result().tokens().get(0).position());
    GrammarNode second = assertGrammar(statements.children().get(1), SyntaxTag.RETURN_STATEMENT, 2);
    assertTerminal(second.children().get(0), f.result().tokens().get(5));
    assertEquals(new SourcePosition(3, 2), f.result().tokens().get(5).position());
    assertEquals("}", f.cursor().peek().orElseThrow().value());
  }

  @Test
  void permitsConsecutiveStatementsOfTheSameKind() {
    Fixture f = fixture("let x = 1; let x = 2; }");

    GrammarNode statements = f.parser().parseStatements();

    assertGrammar(statements, SyntaxTag.STATEMENTS, 2);
    assertGrammar(statements.children().get(0), SyntaxTag.LET_STATEMENT, 5);
    assertGrammar(statements.children().get(1), SyntaxTag.LET_STATEMENT, 5);
    assertEquals("}", f.cursor().peek().orElseThrow().value());
  }

  @Test
  void reportsIfAndWhileAsUnsupportedInThisIncrementWithoutConsumingThem() {
    for (String source : List.of("if (true) { }", "while (true) { }")) {
      Fixture f = fixture(source);
      Token unexpected = f.result().tokens().get(0);

      SyntaxException error =
          assertThrows(SyntaxException.class, () -> f.parser().parseStatements());

      assertEquals("statement (let, do or return)", error.expected());
      assertSame(unexpected, error.found().orElseThrow());
      assertSame(unexpected, f.cursor().peek().orElseThrow());
    }
  }

  @Test
  void rejectsUnexpectedKeywordsIdentifiersAndSymbolsWithoutConsumingThem() {
    for (String source : List.of("var int x;", "name", ";", "class A {}")) {
      Fixture f = fixture(source);
      Token unexpected = f.result().tokens().get(0);

      SyntaxException error =
          assertThrows(SyntaxException.class, () -> f.parser().parseStatements());

      assertEquals("statement (let, do or return)", error.expected());
      assertSame(unexpected, error.found().orElseThrow());
      assertEquals(unexpected.position(), error.position());
      assertSame(unexpected, f.cursor().peek().orElseThrow());
    }
  }

  @Test
  void rejectsInvalidStatementAfterValidStatementAtExactPosition() {
    Fixture f = fixture("let x = 1; unknown;");

    SyntaxException error = assertThrows(SyntaxException.class, () -> f.parser().parseStatements());

    Token unexpected = f.result().tokens().get(5);
    assertSame(unexpected, error.found().orElseThrow());
    assertEquals(unexpected.position(), error.position());
    assertSame(unexpected, f.cursor().peek().orElseThrow());
  }

  @Test
  void returnsImmutableOrderedStatementChildren() {
    Fixture f = fixture("do run(); return; }");

    GrammarNode statements = f.parser().parseStatements();

    assertGrammar(statements, SyntaxTag.STATEMENTS, 2);
    assertEquals(SyntaxTag.DO_STATEMENT, ((GrammarNode) statements.children().get(0)).tag());
    assertEquals(SyntaxTag.RETURN_STATEMENT, ((GrammarNode) statements.children().get(1)).tag());
    assertThrows(
        UnsupportedOperationException.class,
        () -> statements.children().add(statements.children().get(0)));
  }
}
