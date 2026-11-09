package jackcompiler.compiler;

import static jackcompiler.compiler.ExpressionCompilerTestSupport.catalog;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.expression;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.integer;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.symbol;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.term;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.terminal;
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
import jackcompiler.token.SourcePosition;
import jackcompiler.token.TokenType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests Jack assignments, subroutine-call statements, and return statements. */
class StatementCompilerTest {

  @Test
  void rejectsNullCompilationContext() {
    assertThrows(
        NullPointerException.class, () -> new StatementCompiler(null, false, null, "void"));
  }

  @Test
  void rejectsNullAndBlankReturnType() {
    CompilationContext context = context(false);
    assertThrows(
        NullPointerException.class, () -> new StatementCompiler(context, false, null, null));
    assertThrows(
        IllegalArgumentException.class, () -> new StatementCompiler(context, false, null, "  "));
  }

  @Test
  void rejectsCatalogForAnotherClass() {
    CompilationContext context = context(false);
    SubroutineCatalog other = catalog("class Other { function void run() {} }");
    assertThrows(
        IllegalArgumentException.class, () -> new StatementCompiler(context, false, other, "void"));
  }

  @Test
  void rejectsNullStatements() {
    assertThrows(NullPointerException.class, () -> compiler(context(false), "void").compile(null));
  }

  @Test
  void acceptsEmptyStatementsWithoutVmInstructions() {
    CompilationContext context = context(false);
    compiler(context, "void").compile(parseStatements(""));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void assignsIntegerConstantToLocalVariable() {
    CompilationContext context = context(false);
    context.symbols().define("value", "int", SymbolKind.LOCAL);
    compiler(context, "void").compile(parseStatements("let value = 7;"));
    assertEquals("push constant 7\npop local 0\n", context.writer().toVm());
  }

  @Test
  void assignsExpressionToSecondLocalVariable() {
    CompilationContext context = context(false);
    context.symbols().define("first", "int", SymbolKind.LOCAL);
    context.symbols().define("result", "int", SymbolKind.LOCAL);
    compiler(context, "void").compile(parseStatements("let result = first + 3;"));
    assertEquals("push local 0\npush constant 3\nadd\npop local 1\n", context.writer().toVm());
  }

  @Test
  void assignsToStaticVariable() {
    CompilationContext context = context(false);
    context.symbols().define("count", "int", SymbolKind.STATIC);
    compiler(context, "void").compile(parseStatements("let count = 2;"));
    assertEquals("push constant 2\npop static 0\n", context.writer().toVm());
  }

  @Test
  void assignsToFieldInMethod() {
    CompilationContext context = context(true);
    context.symbols().define("x", "int", SymbolKind.FIELD);
    compiler(context, true, null, "void").compile(parseStatements("let x = 9;"));
    assertEquals("push constant 9\npop this 0\n", context.writer().toVm());
  }

  @Test
  void assignsToFieldInConstructor() {
    CompilationContext context = context(false);
    context.symbols().define("x", "int", SymbolKind.FIELD);
    compiler(context, true, null, "Main").compile(parseStatements("let x = 9;"));
    assertEquals("push constant 9\npop this 0\n", context.writer().toVm());
  }

  @Test
  void assignsToMethodArgumentAfterImplicitReceiver() {
    CompilationContext context = context(true);
    context.symbols().define("value", "int", SymbolKind.ARGUMENT);
    compiler(context, true, null, "void").compile(parseStatements("let value = 9;"));
    assertEquals("push constant 9\npop argument 1\n", context.writer().toVm());
  }

  @Test
  void assignsToFunctionArgumentStartingAtZero() {
    CompilationContext context = context(false);
    context.symbols().define("value", "int", SymbolKind.ARGUMENT);
    compiler(context, "void").compile(parseStatements("let value = 9;"));
    assertEquals("push constant 9\npop argument 0\n", context.writer().toVm());
  }

  @Test
  void prefersLocalVariableToShadowedField() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("value", "int", SymbolKind.FIELD);
    context.startSubroutine("run", false);
    context.symbols().define("value", "int", SymbolKind.LOCAL);
    compiler(context, "void").compile(parseStatements("let value = 9;"));
    assertEquals("push constant 9\npop local 0\n", context.writer().toVm());
  }

  @Test
  void compilesAssignmentsInSourceOrder() {
    CompilationContext context = context(false);
    context.symbols().define("a", "int", SymbolKind.LOCAL);
    context.symbols().define("b", "int", SymbolKind.LOCAL);
    compiler(context, "void").compile(parseStatements("let a = 1; let b = a + 2;"));
    assertEquals(
        "push constant 1\npop local 0\n" + "push local 0\npush constant 2\nadd\npop local 1\n",
        context.writer().toVm());
  }

  @Test
  void rejectsUndefinedAssignmentTargetBeforeWritingVm() {
    CompilationContext context = context(false);
    GrammarNode statements = parseStatements("let missing = 4;");
    GrammarNode let = (GrammarNode) statements.children().get(0);
    SourcePosition position = SyntaxTreeNavigator.tokenAt(let, 1, TokenType.IDENTIFIER).position();
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> compiler(context, "void").compile(statements));
    assertTrue(error.description().contains("Undefined Jack variable 'missing'"));
    assertSame(position, error.position().orElseThrow());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void assignmentTargetResolutionIsCaseSensitive() {
    CompilationContext context = context(false);
    context.symbols().define("Value", "int", SymbolKind.LOCAL);
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compiler(context, "void").compile(parseStatements("let value = 1;")));
    assertTrue(error.description().contains("'value'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsAssignmentToFieldInStaticFunction() {
    CompilationContext context = context(false);
    context.symbols().define("size", "int", SymbolKind.FIELD);
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compiler(context, "void").compile(parseStatements("let size = 2;")));
    assertTrue(error.description().contains("Cannot access field 'size'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void assignsFirstElementOfLocalArray() {
    CompilationContext context = arrays(false, "values");
    compiler(context, "void").compile(parseStatements("let values[0] = 7;"));
    assertEquals(
        arrayWrite("push local 0\n", "push constant 0\n", "push constant 7\n"),
        context.writer().toVm());
  }

  @Test
  void assignsArrayElementUsingVariableIndex() {
    CompilationContext context = arrays(false, "values");
    context.symbols().define("index", "int", SymbolKind.LOCAL);
    compiler(context, "void").compile(parseStatements("let values[index + 1] = 9;"));
    assertEquals(
        arrayWrite("push local 0\n", "push local 1\npush constant 1\nadd\n", "push constant 9\n"),
        context.writer().toVm());
  }

  @Test
  void assignsToStaticArray() {
    CompilationContext context = context(false);
    context.symbols().define("values", "Array", SymbolKind.STATIC);
    compiler(context, "void").compile(parseStatements("let values[2] = 5;"));
    assertEquals(
        arrayWrite("push static 0\n", "push constant 2\n", "push constant 5\n"),
        context.writer().toVm());
  }

  @Test
  void assignsToFieldArrayInMethod() {
    CompilationContext context = context(true);
    context.symbols().define("values", "Array", SymbolKind.FIELD);
    compiler(context, true, null, "void").compile(parseStatements("let values[2] = 5;"));
    assertEquals(
        arrayWrite("push this 0\n", "push constant 2\n", "push constant 5\n"),
        context.writer().toVm());
  }

  @Test
  void assignsToArgumentArray() {
    CompilationContext context = context(false);
    context.symbols().define("values", "Array", SymbolKind.ARGUMENT);
    compiler(context, "void").compile(parseStatements("let values[2] = 5;"));
    assertEquals(
        arrayWrite("push argument 0\n", "push constant 2\n", "push constant 5\n"),
        context.writer().toVm());
  }

  @Test
  void restoresThatAfterArrayReadOnAssignmentRightSide() {
    CompilationContext context = arrays(false, "left", "right");
    compiler(context, "void").compile(parseStatements("let left[1] = right[2];"));
    assertEquals(
        "push local 0\npush constant 1\nadd\n"
            + "push local 1\npush constant 2\nadd\npop pointer 1\npush that 0\n"
            + storeArrayValue(),
        context.writer().toVm());
  }

  @Test
  void restoresThatAfterNestedArrayReadInIndexAndValue() {
    CompilationContext context = arrays(false, "target", "indices", "source");
    compiler(context, "void").compile(parseStatements("let target[indices[0]] = source[1];"));
    assertEquals(
        "push local 0\n"
            + "push local 1\npush constant 0\nadd\npop pointer 1\npush that 0\n"
            + "add\n"
            + "push local 2\npush constant 1\nadd\npop pointer 1\npush that 0\n"
            + storeArrayValue(),
        context.writer().toVm());
  }

  @Test
  void assignsCallResultToArrayElement() {
    CompilationContext context = arrays(false, "values");
    compiler(context, "void").compile(parseStatements("let values[1] = Math.multiply(2, 3);"));
    assertEquals(
        arrayWrite(
            "push local 0\n",
            "push constant 1\n",
            "push constant 2\npush constant 3\ncall Math.multiply 2\n"),
        context.writer().toVm());
  }

  @Test
  void rejectsUndefinedIndexedAssignmentTarget() {
    CompilationContext context = context(false);
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compiler(context, "void").compile(parseStatements("let missing[0] = 5;")));
    assertTrue(error.description().contains("Undefined Jack variable 'missing'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsIndexedFieldAssignmentInsideStaticFunction() {
    CompilationContext context = context(false);
    context.symbols().define("values", "Array", SymbolKind.FIELD);
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compiler(context, "void").compile(parseStatements("let values[0] = 5;")));
    assertTrue(error.description().contains("Cannot access field 'values'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void compilesExternalDoCallAndDiscardsReturnValue() {
    CompilationContext context = context(false);
    compiler(context, "void").compile(parseStatements("do Output.printInt(5);"));
    assertEquals("push constant 5\ncall Output.printInt 1\npop temp 0\n", context.writer().toVm());
  }

  @Test
  void compilesNoArgumentDoCall() {
    CompilationContext context = context(false);
    compiler(context, "void").compile(parseStatements("do Keyboard.readChar();"));
    assertEquals("call Keyboard.readChar 0\npop temp 0\n", context.writer().toVm());
  }

  @Test
  void compilesObjectMethodCallInDoStatement() {
    CompilationContext context = context(false);
    context.symbols().define("point", "Point", SymbolKind.LOCAL);
    compiler(context, "void").compile(parseStatements("do point.move(2, 3);"));
    assertEquals(
        "push local 0\npush constant 2\npush constant 3\n" + "call Point.move 3\npop temp 0\n",
        context.writer().toVm());
  }

  @Test
  void compilesLocalMethodDoCallWithImplicitThis() {
    CompilationContext context = context(true);
    SubroutineCatalog catalog = catalog("class Main { method void reset() {} }");
    compiler(context, true, catalog, "void").compile(parseStatements("do reset();"));
    assertEquals("push pointer 0\ncall Main.reset 1\npop temp 0\n", context.writer().toVm());
  }

  @Test
  void compilesUnqualifiedFunctionDoCallWithoutReceiver() {
    CompilationContext context = context(false);
    SubroutineCatalog catalog = catalog("class Main { function void helper(int n) {} }");
    compiler(context, false, catalog, "void").compile(parseStatements("do helper(9);"));
    assertEquals("push constant 9\ncall Main.helper 1\npop temp 0\n", context.writer().toVm());
  }

  @Test
  void compilesNestedCallsWithinDoArguments() {
    CompilationContext context = context(false);
    compiler(context, "void").compile(parseStatements("do Output.printInt(Math.multiply(2, 3));"));
    assertEquals(
        "push constant 2\npush constant 3\ncall Math.multiply 2\n"
            + "call Output.printInt 1\npop temp 0\n",
        context.writer().toVm());
  }

  @Test
  void compilesTwoDoStatementsWithSeparateDiscards() {
    CompilationContext context = context(false);
    compiler(context, "void")
        .compile(parseStatements("do Output.printInt(1); do Output.printInt(2);"));
    assertEquals(
        "push constant 1\ncall Output.printInt 1\npop temp 0\n"
            + "push constant 2\ncall Output.printInt 1\npop temp 0\n",
        context.writer().toVm());
  }

  @Test
  void rejectsUnknownLocalDoCallWithoutWritingVm() {
    CompilationContext context = context(false);
    SubroutineCatalog catalog = catalog("class Main { function void known() {} }");
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                compiler(context, false, catalog, "void")
                    .compile(parseStatements("do missing();")));
    assertTrue(error.description().contains("Undefined subroutine 'missing'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsLocalMethodDoCallInsideStaticFunction() {
    CompilationContext context = context(false);
    SubroutineCatalog catalog = catalog("class Main { method void reset() {} }");
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                compiler(context, false, catalog, "void").compile(parseStatements("do reset();")));
    assertTrue(error.description().contains("Cannot access method 'reset'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void compilesVoidReturnWithDummyZero() {
    CompilationContext context = context(false);
    compiler(context, "void").compile(parseStatements("return;"));
    assertEquals("push constant 0\nreturn\n", context.writer().toVm());
  }

  @Test
  void compilesIntegerReturnExpression() {
    CompilationContext context = context(false);
    compiler(context, "int").compile(parseStatements("return 1 + 2;"));
    assertEquals("push constant 1\npush constant 2\nadd\nreturn\n", context.writer().toVm());
  }

  @Test
  void returnsLocalVariable() {
    CompilationContext context = context(false);
    context.symbols().define("value", "int", SymbolKind.LOCAL);
    compiler(context, "int").compile(parseStatements("return value;"));
    assertEquals("push local 0\nreturn\n", context.writer().toVm());
  }

  @Test
  void returnsArrayElement() {
    CompilationContext context = arrays(false, "values");
    compiler(context, "int").compile(parseStatements("return values[2];"));
    assertEquals(
        "push local 0\npush constant 2\nadd\n" + "pop pointer 1\npush that 0\nreturn\n",
        context.writer().toVm());
  }

  @Test
  void returnsFunctionCallResult() {
    CompilationContext context = context(false);
    compiler(context, "int").compile(parseStatements("return Math.multiply(2, 3);"));
    assertEquals(
        "push constant 2\npush constant 3\n" + "call Math.multiply 2\nreturn\n",
        context.writer().toVm());
  }

  @Test
  void returnsThisFromConstructor() {
    CompilationContext context = context(false);
    compiler(context, true, null, "Main").compile(parseStatements("return this;"));
    assertEquals("push pointer 0\nreturn\n", context.writer().toVm());
  }

  @Test
  void rejectsValueReturnFromVoidSubroutine() {
    CompilationContext context = context(false);
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compiler(context, "void").compile(parseStatements("return 1;")));
    assertTrue(error.description().contains("void Jack subroutine cannot return a value"));
    assertTrue(error.position().isPresent());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsEmptyReturnFromNonVoidSubroutine() {
    CompilationContext context = context(false);
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compiler(context, "int").compile(parseStatements("return;")));
    assertTrue(error.description().contains("non-void Jack subroutine must return a value"));
    assertTrue(error.position().isPresent());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsWrongRootProduction() {
    CompilationContext context = context(false);
    GrammarNode incorrect = new GrammarNode(SyntaxTag.LET_STATEMENT, List.of());
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> compiler(context, "void").compile(incorrect));
    assertTrue(error.description().contains("Expected Jack statements"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsTerminalAsStatementWithOriginalPosition() {
    CompilationContext context = context(false);
    ParseNode token = terminal(TokenType.KEYWORD, "let", 7);
    GrammarNode malformed = statements(token);
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> compiler(context, "void").compile(malformed));
    assertEquals(new SourcePosition(3, 7), error.position().orElseThrow());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsMalformedIfShapeWithoutGeneratingVm() {
    CompilationContext context = context(false);
    GrammarNode malformed = statements(new GrammarNode(SyntaxTag.IF_STATEMENT, List.of()));
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> compiler(context, "void").compile(malformed));
    assertTrue(error.description().contains("Malformed Jack if statement"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsMalformedWhileShapeWithoutGeneratingVm() {
    CompilationContext context = context(false);
    GrammarNode malformed = statements(new GrammarNode(SyntaxTag.WHILE_STATEMENT, List.of()));
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> compiler(context, "void").compile(malformed));
    assertTrue(error.description().contains("Malformed Jack while statement"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsMalformedLetShape() {
    CompilationContext context = context(false);
    GrammarNode malformed =
        statements(new GrammarNode(SyntaxTag.LET_STATEMENT, List.of(symbol("=", 2))));
    assertThrows(CompilationException.class, () -> compiler(context, "void").compile(malformed));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsMalformedAssignmentOperatorWithoutGeneratingVm() {
    CompilationContext context = context(false);
    context.symbols().define("value", "int", SymbolKind.LOCAL);
    GrammarNode malformed =
        statements(
            new GrammarNode(
                SyntaxTag.LET_STATEMENT,
                List.of(
                    terminal(TokenType.KEYWORD, "let", 1),
                    terminal(TokenType.IDENTIFIER, "value", 5),
                    symbol("+", 11),
                    expression(term(integer("1", 13))),
                    symbol(";", 14))));
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> compiler(context, "void").compile(malformed));
    assertTrue(error.description().contains("Expected Jack token '='"));
    assertEquals(new SourcePosition(3, 11), error.position().orElseThrow());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsMalformedArrayAssignmentClosingBracket() {
    CompilationContext context = arrays(false, "values");
    GrammarNode source = (GrammarNode) parseStatements("let values[0] = 1;").children().get(0);
    List<ParseNode> children = new ArrayList<>(source.children());
    children.set(4, symbol(")", 9));
    GrammarNode malformed = statements(new GrammarNode(SyntaxTag.LET_STATEMENT, children));
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> compiler(context, "void").compile(malformed));
    assertTrue(error.description().contains("Expected Jack token ']'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsMalformedDoTerminatorBeforeCallEmission() {
    CompilationContext context = context(false);
    GrammarNode source = (GrammarNode) parseStatements("do Output.printInt(1);").children().get(0);
    List<ParseNode> children = new ArrayList<>(source.children());
    children.set(children.size() - 1, symbol(")", 12));
    GrammarNode malformed = statements(new GrammarNode(SyntaxTag.DO_STATEMENT, children));
    assertThrows(CompilationException.class, () -> compiler(context, "void").compile(malformed));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsMalformedDoShape() {
    CompilationContext context = context(false);
    GrammarNode malformed =
        statements(new GrammarNode(SyntaxTag.DO_STATEMENT, List.of(symbol(";", 1))));
    assertThrows(CompilationException.class, () -> compiler(context, "void").compile(malformed));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsMalformedReturnShape() {
    CompilationContext context = context(false);
    GrammarNode malformed = statements(new GrammarNode(SyntaxTag.RETURN_STATEMENT, List.of()));
    assertThrows(CompilationException.class, () -> compiler(context, "void").compile(malformed));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsMalformedReturnTerminator() {
    CompilationContext context = context(false);
    GrammarNode malformed =
        statements(
            new GrammarNode(
                SyntaxTag.RETURN_STATEMENT,
                List.of(terminal(TokenType.KEYWORD, "return", 1), symbol(")", 7))));
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> compiler(context, "void").compile(malformed));
    assertEquals(new SourcePosition(3, 7), error.position().orElseThrow());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsMalformedReturnExpressionNode() {
    CompilationContext context = context(false);
    GrammarNode malformed =
        statements(
            new GrammarNode(
                SyntaxTag.RETURN_STATEMENT,
                List.of(
                    terminal(TokenType.KEYWORD, "return", 1),
                    term(integer("1", 8)),
                    symbol(";", 9))));
    CompilationException error =
        assertThrows(CompilationException.class, () -> compiler(context, "int").compile(malformed));
    assertTrue(error.description().contains("Expected Jack grammar node EXPRESSION"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void compilesAssignmentDoAndReturnInSourceOrder() {
    CompilationContext context = context(false);
    context.symbols().define("n", "int", SymbolKind.LOCAL);
    compiler(context, "void").compile(parseStatements("let n = 3; do Output.printInt(n); return;"));
    assertEquals(
        "push constant 3\npop local 0\n"
            + "push local 0\ncall Output.printInt 1\npop temp 0\n"
            + "push constant 0\nreturn\n",
        context.writer().toVm());
  }

  private static StatementCompiler compiler(CompilationContext context, String returnType) {
    return compiler(context, false, null, returnType);
  }

  private static StatementCompiler compiler(
      CompilationContext context,
      boolean instanceAccessAllowed,
      SubroutineCatalog catalog,
      String returnType) {
    return new StatementCompiler(context, instanceAccessAllowed, catalog, returnType);
  }

  private static CompilationContext context(boolean isMethod) {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", isMethod);
    return context;
  }

  private static CompilationContext arrays(boolean isMethod, String... names) {
    CompilationContext context = context(isMethod);
    for (String name : names) {
      context.symbols().define(name, "Array", SymbolKind.LOCAL);
    }
    return context;
  }

  private static GrammarNode parseStatements(String bodySource) {
    String source = "class Main { function void run() { " + bodySource + " } }";
    GrammarNode root = new JackParser().parse(new JackTokenizer().tokenize(source));
    GrammarNode subroutine =
        SyntaxTreeNavigator.grammarChildren(root, SyntaxTag.SUBROUTINE_DEC).get(0);
    GrammarNode body = SyntaxTreeNavigator.grammarAt(subroutine, 6, SyntaxTag.SUBROUTINE_BODY);
    return SyntaxTreeNavigator.grammarChildren(body, SyntaxTag.STATEMENTS).get(0);
  }

  private static GrammarNode statements(ParseNode... children) {
    return new GrammarNode(SyntaxTag.STATEMENTS, List.of(children));
  }

  private static String arrayWrite(String base, String index, String value) {
    return base + index + "add\n" + value + storeArrayValue();
  }

  private static String storeArrayValue() {
    return "pop temp 0\npop pointer 1\npush temp 0\npop that 0\n";
  }
}
