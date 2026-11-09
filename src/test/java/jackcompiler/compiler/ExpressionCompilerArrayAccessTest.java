package jackcompiler.compiler;

import static jackcompiler.compiler.ExpressionCompilerTestSupport.expression;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.integer;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.parseExpression;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.symbol;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.term;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.terminal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.symbol.SymbolKind;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.TokenType;
import org.junit.jupiter.api.Test;

/** Tests array reads, address computation, nested indices, and related diagnostics. */
class ExpressionCompilerArrayAccessTest {

  @Test
  void readsFirstElementOfLocalArray() {
    CompilationContext context = locals("values");
    new ExpressionCompiler(context, false).compile(parseExpression("values[0]"));
    assertEquals(read("push local 0\n", "push constant 0\n"), context.writer().toVm());
  }

  @Test
  void usesAssignedLocalIndexForArrayBase() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    context.symbols().define("temporary", "int", SymbolKind.LOCAL);
    context.symbols().define("values", "Array", SymbolKind.LOCAL);
    new ExpressionCompiler(context, false).compile(parseExpression("values[3]"));
    assertEquals(read("push local 1\n", "push constant 3\n"), context.writer().toVm());
  }

  @Test
  void readsStaticArrayWithItsOwnIndexSpace() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("first", "Array", SymbolKind.STATIC);
    context.symbols().define("second", "Array", SymbolKind.STATIC);
    new ExpressionCompiler(context, false).compile(parseExpression("second[2]"));
    assertEquals(read("push static 1\n", "push constant 2\n"), context.writer().toVm());
  }

  @Test
  void readsFieldArrayThroughThisSegmentInMethod() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("other", "Array", SymbolKind.FIELD);
    context.symbols().define("values", "Array", SymbolKind.FIELD);
    context.startSubroutine("read", true);
    new ExpressionCompiler(context, true).compile(parseExpression("values[1]"));
    assertEquals(read("push this 1\n", "push constant 1\n"), context.writer().toVm());
  }

  @Test
  void readsFieldArrayInConstructorContext() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("values", "Array", SymbolKind.FIELD);
    context.startSubroutine("new", false);
    new ExpressionCompiler(context, true).compile(parseExpression("values[0]"));
    assertEquals(read("push this 0\n", "push constant 0\n"), context.writer().toVm());
  }

  @Test
  void readsArgumentArrayInFunction() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("read", false);
    context.symbols().define("values", "Array", SymbolKind.ARGUMENT);
    new ExpressionCompiler(context, false).compile(parseExpression("values[4]"));
    assertEquals(read("push argument 0\n", "push constant 4\n"), context.writer().toVm());
  }

  @Test
  void accountsForImplicitMethodReceiverInArgumentIndex() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("read", true);
    context.symbols().define("values", "Array", SymbolKind.ARGUMENT);
    new ExpressionCompiler(context, true).compile(parseExpression("values[4]"));
    assertEquals(read("push argument 1\n", "push constant 4\n"), context.writer().toVm());
  }

  @Test
  void compilesVariableIndexAfterArrayBase() {
    CompilationContext context = locals("values");
    context.symbols().define("index", "int", SymbolKind.LOCAL);
    new ExpressionCompiler(context, false).compile(parseExpression("values[index]"));
    assertEquals(read("push local 0\n", "push local 1\n"), context.writer().toVm());
  }

  @Test
  void compilesArithmeticIndexBeforeAddressAddition() {
    CompilationContext context = locals("values");
    context.symbols().define("index", "int", SymbolKind.LOCAL);
    new ExpressionCompiler(context, false).compile(parseExpression("values[index + 2]"));
    assertEquals(
        read("push local 0\n", "push local 1\npush constant 2\nadd\n"), context.writer().toVm());
  }

  @Test
  void compilesParenthesizedIndexUsingJackExpressionOrder() {
    CompilationContext context = locals("values");
    new ExpressionCompiler(context, false).compile(parseExpression("values[(2 + 3) * 4]"));
    assertEquals(
        read(
            "push local 0\n",
            "push constant 2\npush constant 3\nadd\n" + "push constant 4\ncall Math.multiply 2\n"),
        context.writer().toVm());
  }

  @Test
  void compilesUnaryIndexWithoutChangingArrayBase() {
    CompilationContext context = locals("values");
    new ExpressionCompiler(context, false).compile(parseExpression("values[-1]"));
    assertEquals(read("push local 0\n", "push constant 1\nneg\n"), context.writer().toVm());
  }

  @Test
  void compilesIndexContainingExternalFunctionCall() {
    CompilationContext context = locals("values");
    new ExpressionCompiler(context, false).compile(parseExpression("values[Math.abs(-2)]"));
    assertEquals(
        read("push local 0\n", "push constant 2\nneg\ncall Math.abs 1\n"), context.writer().toVm());
  }

  @Test
  void readsNestedArrayUsedAsIndex() {
    CompilationContext context = locals("values", "indices");
    new ExpressionCompiler(context, false).compile(parseExpression("values[indices[1]]"));
    assertEquals(
        "push local 0\n" + read("push local 1\n", "push constant 1\n") + addressTail(),
        context.writer().toVm());
  }

  @Test
  void readsArrayUsingItsOwnElementAsIndex() {
    CompilationContext context = locals("values");
    new ExpressionCompiler(context, false).compile(parseExpression("values[values[0]]"));
    assertEquals(
        "push local 0\n" + read("push local 0\n", "push constant 0\n") + addressTail(),
        context.writer().toVm());
  }

  @Test
  void readsNestedIndexCombinedWithArithmetic() {
    CompilationContext context = locals("values", "indices");
    new ExpressionCompiler(context, false).compile(parseExpression("values[indices[1] + 2]"));
    assertEquals(
        "push local 0\n"
            + read("push local 1\n", "push constant 1\n")
            + "push constant 2\nadd\n"
            + addressTail(),
        context.writer().toVm());
  }

  @Test
  void readsArraysOnBothSidesOfBinaryExpression() {
    CompilationContext context = locals("left", "right");
    new ExpressionCompiler(context, false).compile(parseExpression("left[0] + right[1]"));
    assertEquals(
        read("push local 0\n", "push constant 0\n")
            + read("push local 1\n", "push constant 1\n")
            + "add\n",
        context.writer().toVm());
  }

  @Test
  void readsArrayBeforeApplyingUnaryNot() {
    CompilationContext context = locals("values");
    new ExpressionCompiler(context, false).compile(parseExpression("~values[0]"));
    assertEquals(read("push local 0\n", "push constant 0\n") + "not\n", context.writer().toVm());
  }

  @Test
  void readsArrayInsideGroupedExpression() {
    CompilationContext context = locals("values");
    new ExpressionCompiler(context, false).compile(parseExpression("(values[1] + 2)"));
    assertEquals(
        read("push local 0\n", "push constant 1\n") + "push constant 2\nadd\n",
        context.writer().toVm());
  }

  @Test
  void readsArrayAsArgumentOfExternalCall() {
    CompilationContext context = locals("values");
    new ExpressionCompiler(context, false).compile(parseExpression("Output.printInt(values[2])"));
    assertEquals(
        read("push local 0\n", "push constant 2\n") + "call Output.printInt 1\n",
        context.writer().toVm());
  }

  @Test
  void readsTwoArraysAsArgumentsOfExternalCall() {
    CompilationContext context = locals("values", "other");
    new ExpressionCompiler(context, false)
        .compile(parseExpression("Math.multiply(values[0], other[1])"));
    assertEquals(
        read("push local 0\n", "push constant 0\n")
            + read("push local 1\n", "push constant 1\n")
            + "call Math.multiply 2\n",
        context.writer().toVm());
  }

  @Test
  void compilesMultipleIndependentArrayReadsInSameContext() {
    CompilationContext context = locals("values");
    ExpressionCompiler compiler = new ExpressionCompiler(context, false);
    compiler.compile(parseExpression("values[1]"));
    compiler.compile(parseExpression("values[2]"));
    assertEquals(
        read("push local 0\n", "push constant 1\n") + read("push local 0\n", "push constant 2\n"),
        context.writer().toVm());
  }

  @Test
  void resolvesShadowingVariableBeforeClassField() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("values", "Array", SymbolKind.FIELD);
    context.startSubroutine("read", false);
    context.symbols().define("values", "Array", SymbolKind.LOCAL);
    new ExpressionCompiler(context, false).compile(parseExpression("values[3]"));
    assertEquals(read("push local 0\n", "push constant 3\n"), context.writer().toVm());
  }

  @Test
  void permitsStaticArrayReadInsideStaticFunction() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("values", "Array", SymbolKind.STATIC);
    context.startSubroutine("run", false);
    new ExpressionCompiler(context, false).compile(parseExpression("values[2]"));
    assertEquals(read("push static 0\n", "push constant 2\n"), context.writer().toVm());
  }

  @Test
  void rejectsFieldArrayInsideStaticFunctionBeforeWritingVm() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("values", "Array", SymbolKind.FIELD);
    context.startSubroutine("run", false);
    GrammarNode expression = parseExpression("values[1]");
    SourcePosition position = SyntaxTreeNavigator.firstToken(expression).orElseThrow().position();
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(context, false).compile(expression));
    assertTrue(error.description().contains("Cannot access field 'values'"));
    assertSame(position, error.position().orElseThrow());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsUndefinedArrayVariableWithOriginalPosition() {
    CompilationContext context = new CompilationContext("Main");
    GrammarNode expression = parseExpression("missing[1]");
    SourcePosition position = SyntaxTreeNavigator.firstToken(expression).orElseThrow().position();
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(context, false).compile(expression));
    assertTrue(error.description().contains("Undefined Jack variable 'missing'"));
    assertSame(position, error.position().orElseThrow());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void variableResolutionForArrayBaseIsCaseSensitive() {
    CompilationContext context = locals("Values");
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(context, false).compile(parseExpression("values[0]")));
    assertTrue(error.description().contains("'values'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void reportsUndefinedVariableInsideIndexExpression() {
    CompilationContext context = locals("values");
    GrammarNode expression = parseExpression("values[missing]");
    GrammarNode arrayTerm = SyntaxTreeNavigator.grammarAt(expression, 0, SyntaxTag.TERM);
    GrammarNode index = SyntaxTreeNavigator.grammarAt(arrayTerm, 2, SyntaxTag.EXPRESSION);
    SourcePosition position = SyntaxTreeNavigator.firstToken(index).orElseThrow().position();
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(context, false).compile(expression));
    assertTrue(error.description().contains("Undefined Jack variable 'missing'"));
    assertSame(position, error.position().orElseThrow());
  }

  @Test
  void rejectsArrayTermWithNonIdentifierBase() {
    CompilationContext context = new CompilationContext("Main");
    GrammarNode malformed =
        expression(
            term(
                integer("1", 2),
                symbol("[", 3),
                expression(term(integer("0", 4))),
                symbol("]", 5)));
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(context, false).compile(malformed));
    assertEquals(new SourcePosition(3, 2), error.position().orElseThrow());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsArrayTermWithMissingClosingBracket() {
    CompilationContext context = locals("values");
    GrammarNode malformed =
        expression(
            term(
                terminal(TokenType.IDENTIFIER, "values", 1),
                symbol("[", 7),
                expression(term(integer("1", 8))),
                symbol(")", 9)));
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(context, false).compile(malformed));
    assertTrue(error.description().contains("Expected Jack symbol ']'"));
    assertEquals(new SourcePosition(3, 9), error.position().orElseThrow());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsArrayTermWithWrongIndexGrammar() {
    CompilationContext context = locals("values");
    GrammarNode malformed =
        expression(
            term(
                terminal(TokenType.IDENTIFIER, "values", 1),
                symbol("[", 7),
                term(integer("1", 8)),
                symbol("]", 9)));
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(context, false).compile(malformed));
    assertTrue(error.description().contains("Expected Jack grammar node EXPRESSION"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsArrayTermWithNonSymbolOpeningBracket() {
    CompilationContext context = locals("values");
    GrammarNode malformed =
        expression(
            term(
                terminal(TokenType.IDENTIFIER, "values", 1),
                terminal(TokenType.IDENTIFIER, "[", 7),
                expression(term(integer("1", 8))),
                symbol("]", 9)));
    assertThrows(
        CompilationException.class,
        () -> new ExpressionCompiler(context, false).compile(malformed));
    assertEquals("", context.writer().toVm());
  }

  private static CompilationContext locals(String... names) {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    for (String name : names) {
      context.symbols().define(name, "Array", SymbolKind.LOCAL);
    }
    return context;
  }

  private static String addressTail() {
    return "add\npop pointer 1\npush that 0\n";
  }

  private static String read(String base, String indexInstructions) {
    return base + indexInstructions + addressTail();
  }
}
