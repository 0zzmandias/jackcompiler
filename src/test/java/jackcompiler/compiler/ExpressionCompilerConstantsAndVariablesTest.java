package jackcompiler.compiler;

import static jackcompiler.compiler.ExpressionCompilerTestSupport.compile;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.expression;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.parseExpression;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.parseExpressionWithHeader;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.term;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.terminal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.symbol.SymbolKind;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import org.junit.jupiter.api.Test;

class ExpressionCompilerConstantsAndVariablesTest {

  @Test
  void compilesTrueAsNegativeOne() {
    assertEquals("push constant 0\nnot\n", compile("true"));
  }

  @Test
  void compilesFalseAndNullAsZero() {
    assertEquals("push constant 0\n", compile("false"));
    assertEquals("push constant 0\n", compile("null"));
  }

  @Test
  void compilesThisInsideMethod() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("self", true);
    new ExpressionCompiler(context, true)
        .compile(parseExpressionWithHeader("this", "method Main self()"));
    assertEquals("push pointer 0\n", context.writer().toVm());
  }

  @Test
  void compilesThisInsideConstructor() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("new", false);
    new ExpressionCompiler(context, true)
        .compile(parseExpressionWithHeader("this", "constructor Main new()"));
    assertEquals("push pointer 0\n", context.writer().toVm());
  }

  @Test
  void rejectsThisInsideStaticFunction() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    GrammarNode expression = parseExpressionWithHeader("this", "function Main run()");
    SourcePosition position = SyntaxTreeNavigator.firstToken(expression).orElseThrow().position();
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(context, false).compile(expression));
    assertTrue(error.description().contains("Cannot access this"));
    assertSame(position, error.position().orElseThrow());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsUnexpectedKeywordInMalformedTerm() {
    GrammarNode malformed = expression(term(terminal(TokenType.KEYWORD, "return", 2)));
    CompilationContext context = new CompilationContext("Main");
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> new ExpressionCompiler(context).compile(malformed));
    assertTrue(error.description().contains("Unsupported Jack keyword constant"));
    assertEquals(new SourcePosition(3, 2), error.position().orElseThrow());
  }

  @Test
  void compilesEmptyString() {
    assertEquals("push constant 0\ncall String.new 1\n", compile("\"\""));
  }

  @Test
  void compilesSingleCharacterString() {
    assertEquals(
        "push constant 1\n"
            + "call String.new 1\n"
            + "push constant 65\n"
            + "call String.appendChar 2\n",
        compile("\"A\""));
  }

  @Test
  void compilesAsciiStringCharacterByCharacter() {
    assertEquals(
        "push constant 3\n"
            + "call String.new 1\n"
            + "push constant 67\n"
            + "call String.appendChar 2\n"
            + "push constant 97\n"
            + "call String.appendChar 2\n"
            + "push constant 116\n"
            + "call String.appendChar 2\n",
        compile("\"Cat\""));
  }

  @Test
  void preservesStringSpacesAndSpecialCharacters() {
    assertEquals(
        "push constant 6\n"
            + "call String.new 1\n"
            + "push constant 32\n"
            + "call String.appendChar 2\n"
            + "push constant 97\n"
            + "call String.appendChar 2\n"
            + "push constant 60\n"
            + "call String.appendChar 2\n"
            + "push constant 38\n"
            + "call String.appendChar 2\n"
            + "push constant 62\n"
            + "call String.appendChar 2\n"
            + "push constant 32\n"
            + "call String.appendChar 2\n",
        compile("\" a<&> \""));
  }

  @Test
  void compilesNonAsciiCharacterWithinPositiveVmRange() {
    assertEquals(
        "push constant 1\n"
            + "call String.new 1\n"
            + "push constant 233\n"
            + "call String.appendChar 2\n",
        compile("\"\u00E9\""));
  }

  @Test
  void representsHighUtf16CodeUnitsWithoutInvalidVmConstants() {
    assertEquals(
        "push constant 2\n"
            + "call String.new 1\n"
            + "push constant 10178\n"
            + "not\n"
            + "call String.appendChar 2\n"
            + "push constant 8703\n"
            + "not\n"
            + "call String.appendChar 2\n",
        compile("\"\uD83D\uDE00\""));
  }

  @Test
  void rejectsStringLongerThanVmAllocationLimit() {
    CompilationContext context = new CompilationContext("Main");
    GrammarNode malformed =
        expression(term(terminal(TokenType.STRING_CONSTANT, "a".repeat(32768), 5)));
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> new ExpressionCompiler(context).compile(malformed));
    assertTrue(error.description().contains("exceeds the supported VM length"));
    assertEquals(new SourcePosition(3, 5), error.position().orElseThrow());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void compilesStaticVariableWithItsAssignedIndex() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("first", "int", SymbolKind.STATIC);
    context.symbols().define("count", "int", SymbolKind.STATIC);
    new ExpressionCompiler(context).compile(parseExpression("count"));
    assertEquals("push static 1\n", context.writer().toVm());
  }

  @Test
  void compilesFieldVariableUsingThisSegment() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("x", "int", SymbolKind.FIELD);
    context.symbols().define("y", "int", SymbolKind.FIELD);
    context.startSubroutine("read", true);
    new ExpressionCompiler(context, true).compile(parseExpression("y"));
    assertEquals("push this 1\n", context.writer().toVm());
  }

  @Test
  void compilesMethodArgumentAfterImplicitReceiver() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("move", true);
    context.symbols().define("dx", "int", SymbolKind.ARGUMENT);
    context.symbols().define("dy", "int", SymbolKind.ARGUMENT);
    new ExpressionCompiler(context, true).compile(parseExpression("dy"));
    assertEquals("push argument 2\n", context.writer().toVm());
  }

  @Test
  void compilesFunctionArgumentStartingAtIndexZero() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    context.symbols().define("value", "int", SymbolKind.ARGUMENT);
    new ExpressionCompiler(context, false).compile(parseExpression("value"));
    assertEquals("push argument 0\n", context.writer().toVm());
  }

  @Test
  void compilesLocalVariableUsingLocalSegment() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    context.symbols().define("temporary", "int", SymbolKind.LOCAL);
    context.symbols().define("result", "int", SymbolKind.LOCAL);
    new ExpressionCompiler(context, false).compile(parseExpression("result"));
    assertEquals("push local 1\n", context.writer().toVm());
  }

  @Test
  void resolvesSubroutineVariableBeforeShadowedField() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("value", "int", SymbolKind.FIELD);
    context.startSubroutine("read", true);
    context.symbols().define("value", "int", SymbolKind.ARGUMENT);
    new ExpressionCompiler(context, true).compile(parseExpression("value"));
    assertEquals("push argument 1\n", context.writer().toVm());
  }

  @Test
  void permitsStaticVariablesAndLocalsInsideFunctions() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("counter", "int", SymbolKind.STATIC);
    context.startSubroutine("run", false);
    context.symbols().define("offset", "int", SymbolKind.LOCAL);
    new ExpressionCompiler(context, false).compile(parseExpression("counter + offset"));
    assertEquals("push static 0\npush local 0\nadd\n", context.writer().toVm());
  }

  @Test
  void permitsFieldAccessInConstructorContext() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("size", "int", SymbolKind.FIELD);
    context.startSubroutine("new", false);
    new ExpressionCompiler(context, true).compile(parseExpression("size"));
    assertEquals("push this 0\n", context.writer().toVm());
  }

  @Test
  void rejectsFieldAccessInsideStaticFunction() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("size", "int", SymbolKind.FIELD);
    context.startSubroutine("run", false);
    GrammarNode expression = parseExpression("size");
    SourcePosition position = SyntaxTreeNavigator.firstToken(expression).orElseThrow().position();
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(context, false).compile(expression));
    assertTrue(error.description().contains("Cannot access field 'size'"));
    assertSame(position, error.position().orElseThrow());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsUndefinedVariableWithOriginalSourcePosition() {
    CompilationContext context = new CompilationContext("Main");
    GrammarNode expression = parseExpression("missing");
    SourcePosition position = SyntaxTreeNavigator.firstToken(expression).orElseThrow().position();
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> new ExpressionCompiler(context).compile(expression));
    assertTrue(error.description().contains("Undefined Jack variable 'missing'"));
    assertSame(position, error.position().orElseThrow());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void variableResolutionRemainsCaseSensitive() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("Count", "int", SymbolKind.STATIC);
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(context).compile(parseExpression("count")));
    assertTrue(error.description().contains("'count'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void compilesVariablesTogetherWithExistingBinaryOperators() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    context.symbols().define("count", "int", SymbolKind.LOCAL);
    new ExpressionCompiler(context, false).compile(parseExpression("(count + 1) * 2"));
    assertEquals(
        "push local 0\n"
            + "push constant 1\n"
            + "add\n"
            + "push constant 2\n"
            + "call Math.multiply 2\n",
        context.writer().toVm());
  }

  @Test
  void compilesUnaryOperatorAppliedToVariable() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("flag", "boolean", SymbolKind.STATIC);
    new ExpressionCompiler(context).compile(parseExpression("~flag"));
    assertEquals("push static 0\nnot\n", context.writer().toVm());
  }

  @Test
  void respectsScopeResetBetweenSubroutines() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("first", false);
    context.symbols().define("temporary", "int", SymbolKind.LOCAL);
    context.startSubroutine("second", false);
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(context, false).compile(parseExpression("temporary")));
    assertTrue(error.description().contains("Undefined Jack variable"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void preservesClassVariablesAcrossSubroutineChanges() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("counter", "int", SymbolKind.STATIC);
    context.startSubroutine("first", false);
    context.startSubroutine("second", false);
    new ExpressionCompiler(context, false).compile(parseExpression("counter"));
    assertEquals("push static 0\n", context.writer().toVm());
  }

  @Test
  void preservesOriginalSourcePositionForUndefinedVariable() {
    GrammarNode parsed = parseExpression("missing");
    Token original = SyntaxTreeNavigator.firstToken(parsed).orElseThrow();
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(new CompilationContext("Main")).compile(parsed));
    assertSame(original.position(), error.position().orElseThrow());
  }
}
