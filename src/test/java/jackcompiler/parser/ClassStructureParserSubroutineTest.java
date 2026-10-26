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
import jackcompiler.token.Token;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassStructureParserSubroutineTest {

  @Test
  void parsesFunctionWithVoidReturnTypeAndEmptyParameters() {
    Fixture f = fixture("function void run() { return; }");

    GrammarNode declaration = f.parser().parseSubroutineDec();

    assertGrammar(declaration, SyntaxTag.SUBROUTINE_DEC, 7);
    assertTerminal(declaration.children().get(0), f.result().tokens().get(0));
    assertTerminal(declaration.children().get(1), f.result().tokens().get(1));
    assertTerminal(declaration.children().get(2), f.result().tokens().get(2));
    assertTerminal(declaration.children().get(3), f.result().tokens().get(3));
    assertGrammar(declaration.children().get(4), SyntaxTag.PARAMETER_LIST, 0);
    assertTerminal(declaration.children().get(5), f.result().tokens().get(4));

    GrammarNode body = assertGrammar(declaration.children().get(6), SyntaxTag.SUBROUTINE_BODY, 3);
    assertGrammar(body.children().get(1), SyntaxTag.STATEMENTS, 1);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesConstructorReturningClassType() {
    Fixture f = fixture("constructor Main new() { return this; }");

    GrammarNode declaration = f.parser().parseSubroutineDec();

    assertGrammar(declaration, SyntaxTag.SUBROUTINE_DEC, 7);
    assertTerminal(declaration.children().get(0), f.result().tokens().get(0));
    assertTerminal(declaration.children().get(1), f.result().tokens().get(1));
    assertEquals("Main", f.result().tokens().get(1).value());
    assertGrammar(declaration.children().get(6), SyntaxTag.SUBROUTINE_BODY, 3);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesMethodWithPrimitiveReturnType() {
    Fixture f = fixture("method int get() { return 42; }");

    GrammarNode declaration = f.parser().parseSubroutineDec();

    assertGrammar(declaration, SyntaxTag.SUBROUTINE_DEC, 7);
    assertTerminal(declaration.children().get(0), f.result().tokens().get(0));
    assertTerminal(declaration.children().get(1), f.result().tokens().get(1));
    assertTerminal(declaration.children().get(2), f.result().tokens().get(2));
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void acceptsEachPrimitiveTypeAsFunctionReturnType() {
    for (String type : List.of("int", "char", "boolean")) {
      Fixture f = fixture("function " + type + " value() { return 0; }");

      GrammarNode declaration = f.parser().parseSubroutineDec();

      assertGrammar(declaration, SyntaxTag.SUBROUTINE_DEC, 7);
      assertTerminal(declaration.children().get(1), f.result().tokens().get(1));
      assertTrue(f.cursor().isAtEnd());
    }
  }

  @Test
  void acceptsClassNameAsReturnTypeWithoutResolvingIt() {
    Fixture f = fixture("function UnknownClass create() { return null; }");

    GrammarNode declaration = f.parser().parseSubroutineDec();

    assertGrammar(declaration, SyntaxTag.SUBROUTINE_DEC, 7);
    assertTerminal(declaration.children().get(1), f.result().tokens().get(1));
    assertEquals("UnknownClass", f.result().tokens().get(1).value());
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesEmptyParameterListWithoutConsumingClosingParenthesis() {
    Fixture f = fixture(")");

    GrammarNode parameters = f.parser().parseParameterList();

    assertGrammar(parameters, SyntaxTag.PARAMETER_LIST, 0);
    assertSame(f.result().tokens().get(0), f.cursor().peek().orElseThrow());
  }

  @Test
  void parsesOneTypedParameter() {
    Fixture f = fixture("int x)");

    GrammarNode parameters = f.parser().parseParameterList();

    assertGrammar(parameters, SyntaxTag.PARAMETER_LIST, 2);
    assertTerminal(parameters.children().get(0), f.result().tokens().get(0));
    assertTerminal(parameters.children().get(1), f.result().tokens().get(1));
    assertSame(f.result().tokens().get(2), f.cursor().peek().orElseThrow());
  }

  @Test
  void parsesMultipleParametersWithPrimitiveAndClassTypes() {
    Fixture f = fixture("int x, Point origin, boolean ready)");

    GrammarNode parameters = f.parser().parseParameterList();

    assertGrammar(parameters, SyntaxTag.PARAMETER_LIST, 8);
    for (int i = 0; i < 8; i++) {
      assertTerminal(parameters.children().get(i), f.result().tokens().get(i));
    }
    assertSame(f.result().tokens().get(8), f.cursor().peek().orElseThrow());
  }

  @Test
  void acceptsUnresolvedClassNameAsParameterType() {
    Fixture f = fixture("UnknownClass instance)");

    GrammarNode parameters = f.parser().parseParameterList();

    assertGrammar(parameters, SyntaxTag.PARAMETER_LIST, 2);
    assertTerminal(parameters.children().get(0), f.result().tokens().get(0));
    assertEquals("UnknownClass", f.result().tokens().get(0).value());
    assertSame(f.result().tokens().get(2), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsVoidAsParameterType() {
    Fixture f = fixture("void x)");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseParameterList());

    assertEquals("type ('int', 'char', 'boolean' or class name)", error.expected());
    assertSame(f.result().tokens().get(0), error.found().orElseThrow());
    assertSame(f.result().tokens().get(0), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresIdentifierAfterParameterType() {
    Fixture f = fixture("int , boolean ready)");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseParameterList());

    assertEquals("token of type IDENTIFIER", error.expected());
    assertSame(f.result().tokens().get(1), error.found().orElseThrow());
    assertSame(f.result().tokens().get(1), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsTrailingCommaInParameterList() {
    Fixture f = fixture("int x,)");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseParameterList());

    assertEquals("type ('int', 'char', 'boolean' or class name)", error.expected());
    assertSame(f.result().tokens().get(3), error.found().orElseThrow());
    assertSame(f.result().tokens().get(3), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsConsecutiveCommasInParameterList() {
    Fixture f = fixture("int x,, boolean ready)");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseParameterList());

    assertEquals("type ('int', 'char', 'boolean' or class name)", error.expected());
    assertSame(f.result().tokens().get(3), error.found().orElseThrow());
    assertSame(f.result().tokens().get(3), f.cursor().peek().orElseThrow());
  }

  @Test
  void reportsOriginalEofWhenParameterTypeIsMissing() {
    Fixture f = fixture("");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseParameterList());

    assertEquals("type ('int', 'char', 'boolean' or class name)", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(f.result().endPosition(), error.position());
  }

  @Test
  void parsesEmptySubroutineBodyWithEmptyStatementsNode() {
    Fixture f = fixture("{}");

    GrammarNode body = f.parser().parseSubroutineBody();

    assertGrammar(body, SyntaxTag.SUBROUTINE_BODY, 3);
    assertTerminal(body.children().get(0), f.result().tokens().get(0));
    assertGrammar(body.children().get(1), SyntaxTag.STATEMENTS, 0);
    assertTerminal(body.children().get(2), f.result().tokens().get(1));
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesLocalDeclarationsBeforeMultipleStatements() {
    Fixture f =
        fixture("{ var int x; var char letter;" + " let x = 1; do Output.printInt(x); return; }");

    GrammarNode body = f.parser().parseSubroutineBody();

    assertGrammar(body, SyntaxTag.SUBROUTINE_BODY, 5);
    assertGrammar(body.children().get(1), SyntaxTag.VAR_DEC, 4);
    assertGrammar(body.children().get(2), SyntaxTag.VAR_DEC, 4);
    GrammarNode statements = assertGrammar(body.children().get(3), SyntaxTag.STATEMENTS, 3);
    assertGrammar(statements.children().get(0), SyntaxTag.LET_STATEMENT, 5);
    assertGrammar(statements.children().get(1), SyntaxTag.DO_STATEMENT, 8);
    assertGrammar(statements.children().get(2), SyntaxTag.RETURN_STATEMENT, 2);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesNestedConditionalAndLoopInsideSubroutineBody() {
    Fixture f =
        fixture(
            "function void run() {"
                + " var int x;"
                + " while (x) { if (x) { do tick(); } }"
                + " return;"
                + "}");

    GrammarNode declaration = f.parser().parseSubroutineDec();

    GrammarNode body = assertGrammar(declaration.children().get(6), SyntaxTag.SUBROUTINE_BODY, 4);
    assertGrammar(body.children().get(1), SyntaxTag.VAR_DEC, 4);
    GrammarNode statements = assertGrammar(body.children().get(2), SyntaxTag.STATEMENTS, 2);
    GrammarNode loop = assertGrammar(statements.children().get(0), SyntaxTag.WHILE_STATEMENT, 7);
    GrammarNode loopBody = assertGrammar(loop.children().get(5), SyntaxTag.STATEMENTS, 1);
    GrammarNode conditional = assertGrammar(loopBody.children().get(0), SyntaxTag.IF_STATEMENT, 7);
    GrammarNode thenBody = assertGrammar(conditional.children().get(5), SyntaxTag.STATEMENTS, 1);
    assertGrammar(thenBody.children().get(0), SyntaxTag.DO_STATEMENT, 6);
    assertGrammar(statements.children().get(1), SyntaxTag.RETURN_STATEMENT, 2);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void permitsEmptyFunctionBodyWithoutSemanticReturnValidation() {
    Fixture f = fixture("function int value() {}");

    GrammarNode declaration = f.parser().parseSubroutineDec();

    GrammarNode body = assertGrammar(declaration.children().get(6), SyntaxTag.SUBROUTINE_BODY, 3);
    assertGrammar(body.children().get(1), SyntaxTag.STATEMENTS, 0);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void doesNotCheckReturnExpressionAgainstDeclaredReturnType() {
    Fixture f = fixture("function void run() { return 1; }");

    GrammarNode declaration = f.parser().parseSubroutineDec();

    GrammarNode body = assertGrammar(declaration.children().get(6), SyntaxTag.SUBROUTINE_BODY, 3);
    GrammarNode statements = assertGrammar(body.children().get(1), SyntaxTag.STATEMENTS, 1);
    assertGrammar(statements.children().get(0), SyntaxTag.RETURN_STATEMENT, 3);
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void leavesFollowingSubroutineDeclarationUnconsumed() {
    Fixture f =
        fixture("function void first() { return; }" + " function void second() { return; }");

    GrammarNode declaration = f.parser().parseSubroutineDec();

    assertGrammar(declaration, SyntaxTag.SUBROUTINE_DEC, 7);
    assertEquals("function", f.cursor().peek().orElseThrow().value());
  }

  @Test
  void rejectsInvalidSubroutineDeclarationKeyword() {
    Fixture f = fixture("field int value;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseSubroutineDec());

    assertEquals(
        "subroutine declaration ('constructor', 'function' or 'method')", error.expected());
    assertSame(f.result().tokens().get(0), error.found().orElseThrow());
    assertSame(f.result().tokens().get(0), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsInvalidReturnTypeKeyword() {
    Fixture f = fixture("function true test() {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseSubroutineDec());

    assertEquals("type ('int', 'char', 'boolean' or class name)", error.expected());
    assertSame(f.result().tokens().get(1), error.found().orElseThrow());
    assertSame(f.result().tokens().get(1), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresReturnTypeAfterSubroutineKind() {
    Fixture f = fixture("function () {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseSubroutineDec());

    assertEquals("type ('int', 'char', 'boolean' or class name)", error.expected());
    assertSame(f.result().tokens().get(1), error.found().orElseThrow());
    assertSame(f.result().tokens().get(1), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresSubroutineIdentifierAfterReturnType() {
    Fixture f = fixture("function void () {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseSubroutineDec());

    assertEquals("token of type IDENTIFIER", error.expected());
    assertSame(f.result().tokens().get(2), error.found().orElseThrow());
    assertSame(f.result().tokens().get(2), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresOpeningParameterParenthesis() {
    Fixture f = fixture("function void run) {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseSubroutineDec());

    assertEquals("SYMBOL '('", error.expected());
    assertSame(f.result().tokens().get(3), error.found().orElseThrow());
    assertSame(f.result().tokens().get(3), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresClosingParameterParenthesis() {
    Fixture f = fixture("function void run(int x { return; }");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseSubroutineDec());

    assertEquals("SYMBOL ')'", error.expected());
    assertSame(f.result().tokens().get(6), error.found().orElseThrow());
    assertSame(f.result().tokens().get(6), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsVoidAsParameterTypeInsideSubroutine() {
    Fixture f = fixture("function void run(void x) {}");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseSubroutineDec());

    assertEquals("type ('int', 'char', 'boolean' or class name)", error.expected());
    assertSame(f.result().tokens().get(4), error.found().orElseThrow());
    assertSame(f.result().tokens().get(4), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresOpeningBraceForSubroutineBody() {
    Fixture f = fixture("function void run() return;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseSubroutineDec());

    assertEquals("SYMBOL '{'", error.expected());
    assertSame(f.result().tokens().get(5), error.found().orElseThrow());
    assertSame(f.result().tokens().get(5), f.cursor().peek().orElseThrow());
  }

  @Test
  void reportsOriginalEofForUnclosedSubroutineBody() {
    Fixture f = fixture("function void run() { return;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseSubroutineDec());

    assertEquals("SYMBOL '}'", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(f.result().endPosition(), error.position());
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void requiresSemicolonBetweenLocalDeclarationsAndStatements() {
    Fixture f = fixture("function void run() { var int x return; }");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseSubroutineDec());

    assertEquals("SYMBOL ';'", error.expected());
    assertEquals("return", f.cursor().peek().orElseThrow().value());
    assertSame(f.cursor().peek().orElseThrow(), error.found().orElseThrow());
  }

  @Test
  void rejectsLocalVariableDeclarationAfterStatementsHaveStarted() {
    Fixture f = fixture("function void run() { return; var int x; }");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseSubroutineDec());

    assertEquals("statement (let, if, while, do or return)", error.expected());
    assertEquals("var", f.cursor().peek().orElseThrow().value());
    assertSame(f.cursor().peek().orElseThrow(), error.found().orElseThrow());
  }

  @Test
  void rejectsFieldDeclarationInsideSubroutineBody() {
    Fixture f = fixture("function void run() { field int x; }");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseSubroutineDec());

    assertEquals("statement (let, if, while, do or return)", error.expected());
    assertEquals("field", f.cursor().peek().orElseThrow().value());
    assertSame(f.cursor().peek().orElseThrow(), error.found().orElseThrow());
  }

  @Test
  void rejectsUnexpectedTokenBeforeClosingSubroutineBrace() {
    Fixture f = fixture("function void run() { return; )");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseSubroutineDec());

    assertEquals("statement (let, if, while, do or return)", error.expected());
    Token unexpected = f.cursor().peek().orElseThrow();
    assertEquals(")", unexpected.value());
    assertSame(unexpected, error.found().orElseThrow());
    assertEquals(unexpected.position(), error.position());
  }
}
