package jackcompiler.parser;

import static jackcompiler.parser.ClassStructureParserTestSupport.assertGrammar;
import static jackcompiler.parser.ClassStructureParserTestSupport.assertTerminal;
import static jackcompiler.parser.ClassStructureParserTestSupport.fixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.parser.ClassStructureParserTestSupport.Fixture;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import org.junit.jupiter.api.Test;

class ClassStructureParserTest {

  @Test
  void rejectsNullCursor() {
    Fixture f = fixture("");

    assertThrows(
        NullPointerException.class,
        () ->
            new ClassStructureParser(
                null, new StatementParser(f.cursor(), new ExpressionParser(f.cursor()))));
  }

  @Test
  void rejectsNullStatementParser() {
    Fixture f = fixture("");

    assertThrows(NullPointerException.class, () -> new ClassStructureParser(f.cursor(), null));
  }

  @Test
  void parsesEmptyClassWithAllOriginalTerminals() {
    Fixture f = fixture("class Empty {}");

    GrammarNode root = f.parser().parseClass();

    assertGrammar(root, SyntaxTag.CLASS, 4);
    for (int i = 0; i < 4; i++) {
      assertTerminal(root.children().get(i), f.result().tokens().get(i));
    }
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesClassWithFieldAndFunctionInGrammarOrder() {
    Fixture f = fixture("class Main { field int count; function void run() { return; } }");

    GrammarNode root = f.parser().parseClass();

    assertGrammar(root, SyntaxTag.CLASS, 6);
    assertTerminal(root.children().get(0), f.result().tokens().get(0));
    assertTerminal(root.children().get(1), f.result().tokens().get(1));
    assertTerminal(root.children().get(2), f.result().tokens().get(2));
    assertGrammar(root.children().get(3), SyntaxTag.CLASS_VAR_DEC, 4);

    GrammarNode subroutine = assertGrammar(root.children().get(4), SyntaxTag.SUBROUTINE_DEC, 7);
    assertGrammar(subroutine.children().get(4), SyntaxTag.PARAMETER_LIST, 0);
    GrammarNode body = assertGrammar(subroutine.children().get(6), SyntaxTag.SUBROUTINE_BODY, 3);
    assertGrammar(body.children().get(1), SyntaxTag.STATEMENTS, 1);

    assertTerminal(root.children().get(5), f.result().tokens().get(f.result().tokens().size() - 1));
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void preservesMultipleClassDeclarationsAndSubroutinesInSourceOrder() {
    Fixture f =
        fixture(
            "class Game {"
                + " static int total;"
                + " field Point position;"
                + " constructor Game new() { return this; }"
                + " function void start() { return; }"
                + " method void update() { return; }"
                + "}");

    GrammarNode root = f.parser().parseClass();

    assertGrammar(root, SyntaxTag.CLASS, 9);
    assertGrammar(root.children().get(3), SyntaxTag.CLASS_VAR_DEC, 4);
    assertGrammar(root.children().get(4), SyntaxTag.CLASS_VAR_DEC, 4);
    assertGrammar(root.children().get(5), SyntaxTag.SUBROUTINE_DEC, 7);
    assertGrammar(root.children().get(6), SyntaxTag.SUBROUTINE_DEC, 7);
    assertGrammar(root.children().get(7), SyntaxTag.SUBROUTINE_DEC, 7);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void acceptsClassWithoutClassVariablesButWithMultipleFunctions() {
    Fixture f =
        fixture(
            "class Utility {"
                + " function int one() { return 1; }"
                + " function int two() { return 2; }"
                + "}");

    GrammarNode root = f.parser().parseClass();

    assertGrammar(root, SyntaxTag.CLASS, 6);
    assertGrammar(root.children().get(3), SyntaxTag.SUBROUTINE_DEC, 7);
    assertGrammar(root.children().get(4), SyntaxTag.SUBROUTINE_DEC, 7);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void preservesOriginalTokenPositionsAcrossWhitespaceAndComments() {
    Fixture f = fixture("// heading\r\n class Main { // comment\n  field int x;\n }\n");

    GrammarNode root = f.parser().parseClass();

    assertGrammar(root, SyntaxTag.CLASS, 5);
    assertTerminal(root.children().get(0), f.result().tokens().get(0));
    assertEquals(new SourcePosition(2, 2), f.result().tokens().get(0).position());

    GrammarNode declaration = assertGrammar(root.children().get(3), SyntaxTag.CLASS_VAR_DEC, 4);
    assertTerminal(declaration.children().get(0), f.result().tokens().get(3));
    assertEquals(new SourcePosition(3, 3), f.result().tokens().get(3).position());

    assertTrue(f.cursor().isAtEnd());
    assertEquals(f.result().endPosition(), f.cursor().position());
  }

  @Test
  void rejectsClassVariableDeclarationAfterSubroutine() {
    Fixture f = fixture("class C { function void f() { return; } field int x; }");

    SyntaxException error = assertThrows(SyntaxException.class, () -> f.parser().parseClass());

    Token unexpected = f.cursor().peek().orElseThrow();
    assertEquals("SYMBOL '}'", error.expected());
    assertEquals("field", unexpected.value());
    assertSame(unexpected, error.found().orElseThrow());
    assertEquals(unexpected.position(), error.position());
  }

  @Test
  void rejectsLocalVariableDeclarationAtClassScope() {
    Fixture f = fixture("class C { var int x; }");

    SyntaxException error = assertThrows(SyntaxException.class, () -> f.parser().parseClass());

    assertEquals("SYMBOL '}'", error.expected());
    assertEquals("var", f.cursor().peek().orElseThrow().value());
    assertSame(f.result().tokens().get(3), error.found().orElseThrow());
  }

  @Test
  void rejectsNestedClassDeclarations() {
    Fixture f = fixture("class Outer { class Inner {} }");

    SyntaxException error = assertThrows(SyntaxException.class, () -> f.parser().parseClass());

    assertEquals("SYMBOL '}'", error.expected());
    assertSame(f.result().tokens().get(3), error.found().orElseThrow());
    assertSame(f.result().tokens().get(3), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsWrongStartingKeywordWithoutConsumingIt() {
    Fixture f = fixture("function void run() {}");

    SyntaxException error = assertThrows(SyntaxException.class, () -> f.parser().parseClass());

    assertEquals("KEYWORD 'class'", error.expected());
    assertSame(f.result().tokens().get(0), error.found().orElseThrow());
    assertSame(f.result().tokens().get(0), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresClassIdentifier() {
    Fixture f = fixture("class {}");

    SyntaxException error = assertThrows(SyntaxException.class, () -> f.parser().parseClass());

    assertEquals("token of type IDENTIFIER", error.expected());
    assertSame(f.result().tokens().get(1), error.found().orElseThrow());
    assertSame(f.result().tokens().get(1), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresOpeningClassBrace() {
    Fixture f = fixture("class C field int x;");

    SyntaxException error = assertThrows(SyntaxException.class, () -> f.parser().parseClass());

    assertEquals("SYMBOL '{'", error.expected());
    assertSame(f.result().tokens().get(2), error.found().orElseThrow());
    assertSame(f.result().tokens().get(2), f.cursor().peek().orElseThrow());
  }

  @Test
  void reportsOriginalEofWhenClassClosingBraceIsMissing() {
    Fixture f = fixture("class C { field int x;");

    SyntaxException error = assertThrows(SyntaxException.class, () -> f.parser().parseClass());

    assertEquals("SYMBOL '}'", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(f.result().endPosition(), error.position());
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void rejectsUnexpectedMemberWithoutConsumingIt() {
    Fixture f = fixture("class C { unknown }");

    SyntaxException error = assertThrows(SyntaxException.class, () -> f.parser().parseClass());

    assertEquals("SYMBOL '}'", error.expected());
    assertSame(f.result().tokens().get(3), error.found().orElseThrow());
    assertSame(f.result().tokens().get(3), f.cursor().peek().orElseThrow());
  }

  @Test
  void leavesTrailingTokensForJackParserToValidateLater() {
    Fixture f = fixture("class A {} class B {}");

    GrammarNode root = f.parser().parseClass();

    assertGrammar(root, SyntaxTag.CLASS, 4);
    assertSame(f.result().tokens().get(4), f.cursor().peek().orElseThrow());
    assertEquals("class", f.cursor().peek().orElseThrow().value());
  }

  @Test
  void returnsImmutableOrderedClassChildren() {
    Fixture f = fixture("class C { field int x; }");

    GrammarNode root = f.parser().parseClass();

    assertGrammar(root, SyntaxTag.CLASS, 5);
    assertGrammar(root.children().get(3), SyntaxTag.CLASS_VAR_DEC, 4);
    assertThrows(
        UnsupportedOperationException.class, () -> root.children().add(root.children().get(0)));
  }
}
