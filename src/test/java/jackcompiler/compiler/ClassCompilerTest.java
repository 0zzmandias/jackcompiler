package jackcompiler.compiler;

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
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.TokenType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests class declarations, static functions, argument/local scopes and full VM emission. */
class ClassCompilerTest {

  @Test
  void rejectsNullClass() {
    assertThrows(NullPointerException.class, () -> new ClassCompiler().compile(null));
  }

  @Test
  void rejectsWrongRootProduction() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ClassCompiler().compile(new GrammarNode(SyntaxTag.STATEMENTS, List.of())));
    assertTrue(error.description().contains("Expected a Jack class declaration"));
  }

  @Test
  void compilesEmptyClassWithoutVmInstructions() {
    assertEquals("", compile("class Empty {}"));
  }

  @Test
  void classVariablesAloneDoNotEmitVmInstructions() {
    assertEquals("", compile("class Main { static int count; field Array items, other; }"));
  }

  @Test
  void compilesVoidFunctionHeaderAndReturn() {
    assertEquals(
        "function Main.run 0\npush constant 0\nreturn\n",
        compile("class Main { function void run() { return; } }"));
  }

  @Test
  void compilesNonVoidFunctionWithExpressionResult() {
    assertEquals(
        "function Main.add 0\npush constant 2\npush constant 3\nadd\nreturn\n",
        compile("class Main { function int add() { return 2 + 3; } }"));
  }

  @Test
  void compilesTwoFunctionsInDeclarationOrder() {
    assertEquals(
        "function Main.first 0\npush constant 1\nreturn\n"
            + "function Main.second 0\npush constant 2\nreturn\n",
        compile(
            "class Main { function int first() { return 1; } "
                + "function int second() { return 2; } }"));
  }

  @Test
  void usesEachClassNameForVmFunctionNames() {
    assertEquals(
        "function MathHelper.id 0\npush constant 1\nreturn\n",
        compile("class MathHelper { function int id() { return 1; } }"));
  }

  @Test
  void countsOneLocalVariable() {
    assertEquals(
        "function Main.run 1\npush constant 7\npop local 0\n" + "push local 0\nreturn\n",
        compile("class Main { function int run() { var int x; let x = 7; return x; } }"));
  }

  @Test
  void countsMultipleNamesInOneLocalDeclaration() {
    assertEquals(
        "function Main.run 3\npush constant 8\npop local 2\n" + "push local 2\nreturn\n",
        compile("class Main { function int run() { var int a, b, c; let c = 8; return c; } }"));
  }

  @Test
  void countsAllLocalDeclarations() {
    assertEquals(
        "function Main.run 4\npush constant 2\npop local 3\n" + "push local 3\nreturn\n",
        compile(
            "class Main { function int run() { var int a, b; var char c; "
                + "var boolean flag; let flag = 2; return flag; } }"));
  }

  @Test
  void compilesSingleParameterAtArgumentZero() {
    assertEquals(
        "function Main.identity 0\npush argument 0\nreturn\n",
        compile("class Main { function int identity(int x) { return x; } }"));
  }

  @Test
  void registersMultipleParametersInDeclarationOrder() {
    assertEquals(
        "function Main.sum 0\npush argument 0\npush argument 1\nadd\n"
            + "push argument 2\nadd\nreturn\n",
        compile("class Main { function int sum(int a, int b, int c) { " + "return a + b + c; } }"));
  }

  @Test
  void acceptsPrimitiveAndClassParameterTypes() {
    assertEquals(
        "function Main.id 0\npush argument 0\nreturn\n",
        compile(
            "class Main { function Array id(Array values, char c, boolean flag) { "
                + "return values; } }"));
  }

  @Test
  void localVariableIndexStartsAtZeroWithParameters() {
    assertEquals(
        "function Main.add 1\npush argument 0\npush constant 1\nadd\n"
            + "pop local 0\npush local 0\nreturn\n",
        compile(
            "class Main { function int add(int x) { var int y; " + "let y = x + 1; return y; } }"));
  }

  @Test
  void localVariableShadowsStaticVariable() {
    assertEquals(
        "function Main.run 1\npush constant 4\npop local 0\n" + "push local 0\nreturn\n",
        compile(
            "class Main { static int value; function int run() { var int value; "
                + "let value = 4; return value; } }"));
  }

  @Test
  void functionReadsStaticVariables() {
    assertEquals(
        "function Main.read 0\npush static 0\nreturn\n",
        compile("class Main { static int count; function int read() { return count; } }"));
  }

  @Test
  void staticVariablesHaveIndependentIndicesFromFields() {
    assertEquals(
        "function Main.sum 0\npush static 0\npush static 1\nadd\nreturn\n",
        compile(
            "class Main { field int x, y; static int a; field char c; "
                + "static int b; function int sum() { return a + b; } }"));
  }

  @Test
  void functionWritesStaticVariableAndReadsResult() {
    assertEquals(
        "function Main.run 0\npush constant 9\npop static 0\n" + "push static 0\nreturn\n",
        compile(
            "class Main { static int count; function int run() { "
                + "let count = 9; return count; } }"));
  }

  @Test
  void functionCanCallAnotherFunctionDeclaredLater() {
    assertEquals(
        "function Main.run 0\npush constant 4\ncall Main.helper 1\nreturn\n"
            + "function Main.helper 0\npush argument 0\nreturn\n",
        compile(
            "class Main { function int run() { return helper(4); } "
                + "function int helper(int value) { return value; } }"));
  }

  @Test
  void functionCanCallEarlierQualifiedCurrentClassFunction() {
    assertEquals(
        "function Main.helper 0\npush constant 1\nreturn\n"
            + "function Main.run 0\ncall Main.helper 0\nreturn\n",
        compile(
            "class Main { function int helper() { return 1; } "
                + "function int run() { return Main.helper(); } }"));
  }

  @Test
  void functionCanCallExternalOsRoutines() {
    assertEquals(
        "function Main.run 0\npush constant 7\ncall Output.printInt 1\n"
            + "pop temp 0\npush constant 0\nreturn\n",
        compile("class Main { function void run() { do Output.printInt(7); return; } }"));
  }

  @Test
  void generatesArrayReadAndWriteWithLocalSymbols() {
    assertEquals(
        "function Main.run 1\n"
            + "push local 0\npush constant 0\nadd\npush constant 9\n"
            + "pop temp 0\npop pointer 1\npush temp 0\npop that 0\n"
            + "push local 0\npush constant 0\nadd\npop pointer 1\n"
            + "push that 0\nreturn\n",
        compile(
            "class Main { function int run() { var Array arr; "
                + "let arr[0] = 9; return arr[0]; } }"));
  }

  @Test
  void generatesIfElseAndWhileWithFunctionScopedLabels() {
    assertEquals(
        "function Main.run 1\n"
            + "push constant 0\npop local 0\n"
            + "label WHILE$0_EXP\npush local 0\npush constant 2\nlt\n"
            + "not\nif-goto WHILE$0_END\n"
            + "push local 0\npush constant 0\neq\nnot\nif-goto IF$1_FALSE\n"
            + "push local 0\npush constant 1\nadd\npop local 0\ngoto IF$1_END\n"
            + "label IF$1_FALSE\npush local 0\npush constant 2\nadd\n"
            + "pop local 0\nlabel IF$1_END\n"
            + "goto WHILE$0_EXP\nlabel WHILE$0_END\n"
            + "push local 0\nreturn\n",
        compile(
            "class Main { function int run() { var int n; let n = 0; "
                + "while (n < 2) { if (n = 0) { let n = n + 1; } "
                + "else { let n = n + 2; } } return n; } }"));
  }

  @Test
  void resetsLocalAndArgumentScopesForEachFunction() {
    assertEquals(
        "function Main.first 1\npush argument 0\npop local 0\n"
            + "push local 0\nreturn\n"
            + "function Main.second 1\npush argument 0\npop local 0\n"
            + "push local 0\nreturn\n",
        compile(
            "class Main { function int first(int x) { var int y; "
                + "let y = x; return y; } function int second(int a) { "
                + "var int b; let b = a; return b; } }"));
  }

  @Test
  void resetsControlFlowLabelCountersBetweenFunctions() {
    assertEquals(
        "function Main.first 0\nlabel WHILE$0_EXP\npush constant 0\nnot\n"
            + "if-goto WHILE$0_END\ngoto WHILE$0_EXP\nlabel WHILE$0_END\n"
            + "push constant 0\nreturn\n"
            + "function Main.second 0\nlabel WHILE$0_EXP\npush constant 0\nnot\n"
            + "if-goto WHILE$0_END\ngoto WHILE$0_EXP\nlabel WHILE$0_END\n"
            + "push constant 0\nreturn\n",
        compile(
            "class Main { function void first() { while (false) {} return; } "
                + "function void second() { while (false) {} return; } }"));
  }

  @Test
  void classCompilerInstanceCanBeReusedWithoutStateLeakage() {
    ClassCompiler compiler = new ClassCompiler();
    assertEquals(
        "function First.run 0\npush constant 1\nreturn\n",
        compiler.compile(parse("class First { function int run() { return 1; } }")));
    assertEquals(
        "function Second.run 0\npush constant 2\nreturn\n",
        compiler.compile(parse("class Second { function int run() { return 2; } }")));
  }

  @Test
  void rejectsMethodUntilObjectCompilationIncrement() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { method void reset() { return; } }"));
    assertTrue(error.description().contains("methods and constructors are not yet supported"));
    assertTrue(error.position().isPresent());
  }

  @Test
  void rejectsConstructorUntilObjectCompilationIncrement() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { constructor Main new() { return this; } }"));
    assertTrue(error.description().contains("methods and constructors are not yet supported"));
    assertTrue(error.position().isPresent());
  }

  @Test
  void rejectsAnyClassContainingUnsupportedMethod() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                compile(
                    "class Main { function void run() { return; } "
                        + "method void other() { return; } }"));
    assertTrue(error.description().contains("not yet supported"));
  }

  @Test
  void rejectsInstanceFieldReadInsideStaticFunction() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                compile(
                    "class Main { field int value; " + "function int read() { return value; } }"));
    assertTrue(error.description().contains("Cannot access field 'value'"));
  }

  @Test
  void rejectsInstanceFieldWriteInsideStaticFunction() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                compile(
                    "class Main { field int value; "
                        + "function void write() { let value = 1; return; } }"));
    assertTrue(error.description().contains("Cannot access field 'value'"));
  }

  @Test
  void rejectsThisInsideStaticFunction() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { function Main current() { return this; } }"));
    assertTrue(error.description().contains("Cannot access this"));
  }

  @Test
  void rejectsUnknownVariableWithOriginalPosition() {
    GrammarNode root = parse("class Main { function int run() { return missing; } }");
    GrammarNode sub = SyntaxTreeNavigator.grammarChildren(root, SyntaxTag.SUBROUTINE_DEC).get(0);
    GrammarNode body = SyntaxTreeNavigator.grammarAt(sub, 6, SyntaxTag.SUBROUTINE_BODY);
    GrammarNode statements = SyntaxTreeNavigator.grammarChildren(body, SyntaxTag.STATEMENTS).get(0);
    GrammarNode ret = (GrammarNode) statements.children().get(0);
    GrammarNode expr = SyntaxTreeNavigator.grammarAt(ret, 1, SyntaxTag.EXPRESSION);
    SourcePosition position = SyntaxTreeNavigator.firstToken(expr).orElseThrow().position();
    CompilationException error =
        assertThrows(CompilationException.class, () -> new ClassCompiler().compile(root));
    assertTrue(error.description().contains("Undefined Jack variable 'missing'"));
    assertSame(position, error.position().orElseThrow());
  }

  @Test
  void rejectsDuplicateClassLevelVariablesAcrossCategories() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { static int value; field char value; }"));
    assertTrue(error.description().contains("duplicate Jack variable 'value'"));
  }

  @Test
  void rejectsDuplicateVariablesInSameClassDeclaration() {
    CompilationException error =
        assertThrows(CompilationException.class, () -> compile("class Main { static int a, a; }"));
    assertTrue(error.description().contains("duplicate Jack variable 'a'"));
  }

  @Test
  void rejectsDuplicateLocalVariablesAcrossDeclarations() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { function void run() { var int x; var char x; return; } }"));
    assertTrue(error.description().contains("duplicate Jack variable 'x'"));
  }

  @Test
  void rejectsDuplicateFunctionParameterNames() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { function void run(int x, char x) { return; } }"));
    assertTrue(error.description().contains("duplicate Jack variable 'x'"));
  }

  @Test
  void rejectsLocalVariableDuplicatingParameterName() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { function void run(int x) { var int x; return; } }"));
    assertTrue(error.description().contains("duplicate Jack variable 'x'"));
  }

  @Test
  void rejectsDuplicateSubroutineNamesInCatalog() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                compile(
                    "class Main { function int f() { return 1; } "
                        + "function int f() { return 2; } }"));
    assertTrue(error.description().contains("Duplicate subroutine 'f'"));
  }

  @Test
  void rejectsIncorrectArityForForwardLocalCall() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                compile(
                    "class Main { function int run() { return helper(); } "
                        + "function int helper(int n) { return n; } }"));
    assertTrue(error.description().contains("expects 1 argument(s), found 0"));
  }

  @Test
  void rejectsReturnValueFromVoidFunction() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { function void run() { return 3; } }"));
    assertTrue(error.description().contains("void Jack subroutine cannot return a value"));
  }

  @Test
  void rejectsMissingReturnValueInNonVoidFunction() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { function int run() { return; } }"));
    assertTrue(error.description().contains("non-void Jack subroutine must return a value"));
  }

  @Test
  void rejectsMalformedClassShape() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ClassCompiler().compile(new GrammarNode(SyntaxTag.CLASS, List.of())));
    assertTrue(error.description().contains("Malformed Jack class declaration"));
  }

  @Test
  void rejectsMalformedClassClosingBraceBeforeWritingVm() {
    GrammarNode root = parse("class Main { function void run() { return; } }");
    GrammarNode malformed = changed(root, root.children().size() - 1, symbol(")", 20));
    CompilationException error =
        assertThrows(CompilationException.class, () -> new ClassCompiler().compile(malformed));
    assertTrue(error.description().contains("Expected Jack token '}'"));
    assertEquals(new SourcePosition(3, 20), error.position().orElseThrow());
  }

  @Test
  void rejectsUnexpectedClassMember() {
    GrammarNode root = parse("class Main {}");
    List<ParseNode> nodes = new ArrayList<>(root.children());
    nodes.add(nodes.size() - 1, term(integer("1", 4)));
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ClassCompiler().compile(new GrammarNode(SyntaxTag.CLASS, nodes)));
    assertTrue(error.description().contains("Unexpected or out-of-order Jack class member"));
  }

  @Test
  void rejectsClassVariableAfterSubroutine() {
    GrammarNode root = parse("class Main { static int count; function void run() { return; } }");
    GrammarNode malformed = changedTwo(root, 3, root.children().get(4), 4, root.children().get(3));
    CompilationException error =
        assertThrows(CompilationException.class, () -> new ClassCompiler().compile(malformed));
    assertTrue(error.description().contains("Unexpected or out-of-order Jack class member"));
  }

  @Test
  void rejectsMalformedClassVariableDeclaration() {
    GrammarNode root = parse("class Main { static int value; }");
    GrammarNode malformed =
        changed(root, 3, new GrammarNode(SyntaxTag.CLASS_VAR_DEC, List.of(symbol(";", 8))));
    CompilationException error =
        assertThrows(CompilationException.class, () -> new ClassCompiler().compile(malformed));
    assertTrue(error.description().contains("Malformed Jack class variable declaration"));
  }

  @Test
  void rejectsWrongClassVariableTypeWithOriginalPosition() {
    GrammarNode root = parse("class Main { static int value; }");
    GrammarNode var = SyntaxTreeNavigator.grammarChildren(root, SyntaxTag.CLASS_VAR_DEC).get(0);
    GrammarNode malformedVar = changed(var, 1, terminal(TokenType.KEYWORD, "void", 12));
    GrammarNode malformed = changed(root, 3, malformedVar);
    CompilationException error =
        assertThrows(CompilationException.class, () -> new ClassCompiler().compile(malformed));
    assertTrue(error.description().contains("Invalid Jack type 'void'"));
    assertEquals(new SourcePosition(3, 12), error.position().orElseThrow());
  }

  @Test
  void rejectsMalformedFunctionBody() {
    GrammarNode root = parse("class Main { function void run() { return; } }");
    GrammarNode sub = SyntaxTreeNavigator.grammarChildren(root, SyntaxTag.SUBROUTINE_DEC).get(0);
    GrammarNode malformedSub =
        changed(sub, 6, new GrammarNode(SyntaxTag.SUBROUTINE_BODY, List.of()));
    GrammarNode malformed = changed(root, 3, malformedSub);
    CompilationException error =
        assertThrows(CompilationException.class, () -> new ClassCompiler().compile(malformed));
    assertTrue(error.description().contains("Malformed Jack subroutine body"));
  }

  @Test
  void rejectsWrongSubroutineBodyStatementsGrammar() {
    GrammarNode root = parse("class Main { function void run() { return; } }");
    GrammarNode sub = SyntaxTreeNavigator.grammarChildren(root, SyntaxTag.SUBROUTINE_DEC).get(0);
    GrammarNode body = SyntaxTreeNavigator.grammarAt(sub, 6, SyntaxTag.SUBROUTINE_BODY);
    GrammarNode badBody = changed(body, 1, term());
    GrammarNode malformed = changed(root, 3, changed(sub, 6, badBody));
    CompilationException error =
        assertThrows(CompilationException.class, () -> new ClassCompiler().compile(malformed));
    assertTrue(error.description().contains("Expected Jack grammar node STATEMENTS"));
  }

  @Test
  void rejectsMalformedLocalDeclaration() {
    GrammarNode root = parse("class Main { function void run() { var int x; return; } }");
    GrammarNode sub = SyntaxTreeNavigator.grammarChildren(root, SyntaxTag.SUBROUTINE_DEC).get(0);
    GrammarNode body = SyntaxTreeNavigator.grammarAt(sub, 6, SyntaxTag.SUBROUTINE_BODY);
    GrammarNode badBody =
        changed(body, 1, new GrammarNode(SyntaxTag.VAR_DEC, List.of(symbol(";", 5))));
    GrammarNode malformed = changed(root, 3, changed(sub, 6, badBody));
    CompilationException error =
        assertThrows(CompilationException.class, () -> new ClassCompiler().compile(malformed));
    assertTrue(error.description().contains("Malformed Jack variable declaration"));
  }

  private static String compile(String source) {
    return new ClassCompiler().compile(parse(source));
  }

  private static GrammarNode parse(String source) {
    return new JackParser().parse(new JackTokenizer().tokenize(source));
  }

  private static GrammarNode changed(GrammarNode original, int index, ParseNode replacement) {
    List<ParseNode> nodes = new ArrayList<>(original.children());
    nodes.set(index, replacement);
    return new GrammarNode(original.tag(), nodes);
  }

  private static GrammarNode changedTwo(
      GrammarNode original, int first, ParseNode firstValue, int second, ParseNode secondValue) {
    List<ParseNode> nodes = new ArrayList<>(original.children());
    nodes.set(first, firstValue);
    nodes.set(second, secondValue);
    return new GrammarNode(original.tag(), nodes);
  }
}
