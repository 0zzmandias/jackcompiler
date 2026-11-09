package jackcompiler.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.parser.JackParser;
import jackcompiler.symbol.SymbolKind;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpressionCompilerTest {

  @Test
  void rejectsNullCompilationContext() {
    assertThrows(NullPointerException.class, () -> new ExpressionCompiler(null));
  }

  @Test
  void rejectsNullCompilationContextWithExplicitInstanceAccess() {
    assertThrows(NullPointerException.class, () -> new ExpressionCompiler(null, true));
    assertThrows(NullPointerException.class, () -> new ExpressionCompiler(null, false));
  }

  @Test
  void rejectsNullExpression() {
    ExpressionCompiler compiler = new ExpressionCompiler(new CompilationContext("Main"));

    assertThrows(NullPointerException.class, () -> compiler.compile(null));
  }

  @Test
  void compilesZeroConstant() {
    assertEquals("push constant 0\n", compile("0"));
  }

  @Test
  void compilesMaximumJackIntegerConstant() {
    assertEquals("push constant 32767\n", compile("32767"));
  }

  @Test
  void compilesIntegerConstantsWithoutChangingTheirDecimalValue() {
    for (int value : new int[] {1, 7, 10, 99, 256, 1000, 16384, 32766}) {
      assertEquals("push constant " + value + "\n", compile(Integer.toString(value)));
    }
  }

  @Test
  void compilesAddition() {
    assertEquals("push constant 7\npush constant 2\nadd\n", compile("7 + 2"));
  }

  @Test
  void compilesSubtraction() {
    assertEquals("push constant 7\npush constant 2\nsub\n", compile("7 - 2"));
  }

  @Test
  void compilesMultiplicationUsingJackMathLibrary() {
    assertEquals("push constant 7\npush constant 2\ncall Math.multiply 2\n", compile("7 * 2"));
  }

  @Test
  void compilesDivisionUsingJackMathLibrary() {
    assertEquals("push constant 8\npush constant 2\ncall Math.divide 2\n", compile("8 / 2"));
  }

  @Test
  void compilesBitwiseAnd() {
    assertEquals("push constant 7\npush constant 3\nand\n", compile("7 & 3"));
  }

  @Test
  void compilesBitwiseOr() {
    assertEquals("push constant 7\npush constant 3\nor\n", compile("7 | 3"));
  }

  @Test
  void compilesLessThanComparison() {
    assertEquals("push constant 2\npush constant 7\nlt\n", compile("2 < 7"));
  }

  @Test
  void compilesGreaterThanComparison() {
    assertEquals("push constant 7\npush constant 2\ngt\n", compile("7 > 2"));
  }

  @Test
  void compilesEqualityComparison() {
    assertEquals("push constant 7\npush constant 7\neq\n", compile("7 = 7"));
  }

  @Test
  void compilesUnaryNegationAfterItsOperand() {
    assertEquals("push constant 7\nneg\n", compile("-7"));
  }

  @Test
  void compilesUnaryBitwiseNotAfterItsOperand() {
    assertEquals("push constant 0\nnot\n", compile("~0"));
  }

  @Test
  void compilesNestedUnaryOperatorsFromInsideOut() {
    assertEquals("push constant 5\nnot\nneg\n", compile("-~5"));
    assertEquals("push constant 5\nneg\nneg\n", compile("--5"));
  }

  @Test
  void compilesParenthesizedExpression() {
    assertEquals("push constant 2\npush constant 3\nadd\n", compile("(2 + 3)"));
  }

  @Test
  void compilesMultipleNestedParentheses() {
    assertEquals("push constant 7\n", compile("((((7))))"));
  }

  @Test
  void respectsExplicitParenthesesBeforeOuterOperation() {
    assertEquals(
        "push constant 2\n"
            + "push constant 3\n"
            + "push constant 4\n"
            + "call Math.multiply 2\n"
            + "add\n",
        compile("2 + (3 * 4)"));
  }

  @Test
  void evaluatesFlatExpressionsFromLeftToRight() {
    assertEquals(
        "push constant 2\n"
            + "push constant 3\n"
            + "add\n"
            + "push constant 4\n"
            + "call Math.multiply 2\n",
        compile("2 + 3 * 4"));
  }

  @Test
  void distinguishesBinaryMinusFromUnaryMinus() {
    assertEquals("push constant 8\npush constant 2\nneg\nsub\n", compile("8 - -2"));
  }

  @Test
  void compilesUnaryOperatorAppliedToGroupedExpression() {
    assertEquals("push constant 5\npush constant 3\nsub\nneg\n", compile("-(5 - 3)"));
  }

  @Test
  void preservesStackInstructionOrderInLongExpression() {
    assertEquals(
        "push constant 1\n"
            + "push constant 2\n"
            + "add\n"
            + "push constant 3\n"
            + "sub\n"
            + "push constant 4\n"
            + "and\n"
            + "push constant 5\n"
            + "or\n",
        compile("1 + 2 - 3 & 4 | 5"));
  }

  @Test
  void accumulatesIndependentExpressionResultsInSharedWriter() {
    CompilationContext context = new CompilationContext("Main");
    ExpressionCompiler compiler = new ExpressionCompiler(context);

    compiler.compile(parseExpression("1 + 2"));
    compiler.compile(parseExpression("~3"));

    assertEquals(
        "push constant 1\npush constant 2\nadd\npush constant 3\nnot\n", context.writer().toVm());
  }

  @Test
  void isolatesDifferentCompilationContexts() {
    CompilationContext first = new CompilationContext("First");
    CompilationContext second = new CompilationContext("Second");

    new ExpressionCompiler(first).compile(parseExpression("1 + 2"));
    new ExpressionCompiler(second).compile(parseExpression("7 * 3"));

    assertEquals("push constant 1\npush constant 2\nadd\n", first.writer().toVm());
    assertEquals(
        "push constant 7\npush constant 3\ncall Math.multiply 2\n", second.writer().toVm());
  }

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
    ExpressionCompiler compiler = new ExpressionCompiler(context, false);

    compiler.compile(parseExpression("counter + offset"));

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
  void rejectsWrongRootProduction() {
    GrammarNode wrong = term(integer("1", 1));
    CompilationContext context = new CompilationContext("Main");

    CompilationException error =
        assertThrows(
            CompilationException.class, () -> new ExpressionCompiler(context).compile(wrong));

    assertTrue(error.description().contains("Expected a Jack expression"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsEmptyAndEvenLengthExpressions() {
    CompilationContext context = new CompilationContext("Main");
    ExpressionCompiler compiler = new ExpressionCompiler(context);

    GrammarNode empty = expression();
    GrammarNode incomplete = expression(term(integer("1", 1)), symbol("+", 3));

    assertThrows(CompilationException.class, () -> compiler.compile(empty));
    assertThrows(CompilationException.class, () -> compiler.compile(incomplete));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsUnexpectedNodesInExpression() {
    GrammarNode malformed = expression(integer("2", 1));

    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(new CompilationContext("Main")).compile(malformed));

    assertTrue(error.description().contains("Expected Jack grammar node TERM"));
    assertEquals(new SourcePosition(3, 1), error.position().orElseThrow());
  }

  @Test
  void rejectsUnsupportedBinaryOperatorWithItsPosition() {
    GrammarNode malformed =
        expression(term(integer("2", 1)), symbol("%", 3), term(integer("3", 5)));

    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(new CompilationContext("Main")).compile(malformed));

    assertTrue(error.description().contains("Unsupported Jack binary operator"));
    assertEquals(new SourcePosition(3, 3), error.position().orElseThrow());
  }

  @Test
  void rejectsNonSymbolBinaryOperator() {
    GrammarNode malformed =
        expression(
            term(integer("1", 1)),
            terminal(TokenType.IDENTIFIER, "plus", 3),
            term(integer("2", 8)));

    assertThrows(
        CompilationException.class,
        () -> new ExpressionCompiler(new CompilationContext("Main")).compile(malformed));
  }

  @Test
  void rejectsMalformedIntegerConstant() {
    CompilationContext context = new CompilationContext("Main");
    GrammarNode malformed = expression(term(integer("1x", 4)));

    CompilationException error =
        assertThrows(
            CompilationException.class, () -> new ExpressionCompiler(context).compile(malformed));

    assertTrue(error.description().contains("Invalid Jack integer constant"));
    assertEquals(new SourcePosition(3, 4), error.position().orElseThrow());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsOutOfRangeIntegerConstants() {
    CompilationContext context = new CompilationContext("Main");
    ExpressionCompiler compiler = new ExpressionCompiler(context);

    assertThrows(
        CompilationException.class, () -> compiler.compile(expression(term(integer("32768", 1)))));
    assertThrows(
        CompilationException.class,
        () -> compiler.compile(expression(term(integer("99999999999999999999", 1)))));

    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsArrayAccessUntilDedicatedIncrement() {
    assertThrows(
        CompilationException.class,
        () ->
            new ExpressionCompiler(new CompilationContext("Main"))
                .compile(parseExpression("values[2]")));
  }

  @Test
  void rejectsSubroutineCallsUntilDedicatedIncrement() {
    assertThrows(
        CompilationException.class,
        () ->
            new ExpressionCompiler(new CompilationContext("Main"))
                .compile(parseExpression("helper(1, 2)")));
    assertThrows(
        CompilationException.class,
        () ->
            new ExpressionCompiler(new CompilationContext("Main"))
                .compile(parseExpression("Math.multiply(2, 3)")));
  }

  @Test
  void rejectsEmptyAndMalformedTerms() {
    CompilationContext context = new CompilationContext("Main");
    ExpressionCompiler compiler = new ExpressionCompiler(context);

    assertThrows(CompilationException.class, () -> compiler.compile(expression(term())));
    assertThrows(
        CompilationException.class, () -> compiler.compile(expression(term(expression()))));

    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsUnsupportedUnaryOperator() {
    GrammarNode malformed = expression(term(symbol("+", 1), term(integer("2", 2))));
    CompilationContext context = new CompilationContext("Main");

    CompilationException error =
        assertThrows(
            CompilationException.class, () -> new ExpressionCompiler(context).compile(malformed));

    assertTrue(error.description().contains("Unsupported Jack unary operator"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsUnaryOperandWithWrongProduction() {
    GrammarNode malformed = expression(term(symbol("-", 1), integer("2", 2)));

    assertThrows(
        CompilationException.class,
        () -> new ExpressionCompiler(new CompilationContext("Main")).compile(malformed));
  }

  @Test
  void rejectsMalformedParenthesizedTerm() {
    GrammarNode malformed =
        expression(term(symbol("(", 1), expression(term(integer("2", 2))), symbol("]", 3)));
    CompilationContext context = new CompilationContext("Main");

    CompilationException error =
        assertThrows(
            CompilationException.class, () -> new ExpressionCompiler(context).compile(malformed));

    assertTrue(error.description().contains("Expected Jack symbol ')'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsGroupingWithoutExpressionChild() {
    GrammarNode malformed = expression(term(symbol("(", 1), term(integer("1", 2)), symbol(")", 3)));

    assertThrows(
        CompilationException.class,
        () -> new ExpressionCompiler(new CompilationContext("Main")).compile(malformed));
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

  @Test
  void rejectsUnsupportedAtomicSymbol() {
    GrammarNode malformed = expression(term(symbol(";", 5)));

    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(new CompilationContext("Main")).compile(malformed));

    assertEquals(new SourcePosition(3, 5), error.position().orElseThrow());
  }

  private static String compile(String sourceExpression) {
    CompilationContext context = new CompilationContext("Main");
    new ExpressionCompiler(context).compile(parseExpression(sourceExpression));
    return context.writer().toVm();
  }

  private static GrammarNode parseExpression(String sourceExpression) {
    return parseExpressionWithHeader(sourceExpression, "function void run()");
  }

  private static GrammarNode parseExpressionWithHeader(
      String sourceExpression, String subroutineHeader) {
    String source = "class Main { " + subroutineHeader + " { return " + sourceExpression + "; } }";

    GrammarNode root = new JackParser().parse(new JackTokenizer().tokenize(source));
    GrammarNode subroutine =
        SyntaxTreeNavigator.grammarChildren(root, SyntaxTag.SUBROUTINE_DEC).get(0);
    GrammarNode body = SyntaxTreeNavigator.grammarAt(subroutine, 6, SyntaxTag.SUBROUTINE_BODY);
    GrammarNode statements = SyntaxTreeNavigator.grammarChildren(body, SyntaxTag.STATEMENTS).get(0);
    GrammarNode statement =
        SyntaxTreeNavigator.grammarChildren(statements, SyntaxTag.RETURN_STATEMENT).get(0);

    return SyntaxTreeNavigator.grammarAt(statement, 1, SyntaxTag.EXPRESSION);
  }

  private static GrammarNode expression(ParseNode... children) {
    return new GrammarNode(SyntaxTag.EXPRESSION, List.of(children));
  }

  private static GrammarNode term(ParseNode... children) {
    return new GrammarNode(SyntaxTag.TERM, List.of(children));
  }

  private static TerminalNode integer(String value, int column) {
    return terminal(TokenType.INTEGER_CONSTANT, value, column);
  }

  private static TerminalNode symbol(String value, int column) {
    return terminal(TokenType.SYMBOL, value, column);
  }

  private static TerminalNode terminal(TokenType type, String value, int column) {
    return new TerminalNode(new Token(type, value, new SourcePosition(3, column)));
  }
}
