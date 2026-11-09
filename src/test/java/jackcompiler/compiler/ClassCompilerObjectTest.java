package jackcompiler.compiler;

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

/** Tests instance methods and constructors across complete Jack classes. */
class ClassCompilerObjectTest {

  @Test
  void methodInitializesReceiverBeforeReturningConstant() {
    assertEquals(
        method("Main.id", 0) + "push constant 7\nreturn\n",
        compile("class Main { method int id() { return 7; } }"));
  }

  @Test
  void methodUsesArgumentOneForItsFirstExplicitParameter() {
    assertEquals(
        method("Main.id", 0) + "push argument 1\nreturn\n",
        compile("class Main { method int id(int value) { return value; } }"));
  }

  @Test
  void methodUsesSuccessiveExplicitArgumentIndices() {
    assertEquals(
        method("Main.add", 0) + "push argument 1\npush argument 2\nadd\nreturn\n",
        compile("class Main { method int add(int a, int b) { return a + b; } }"));
  }

  @Test
  void methodLocalsBeginAtZeroRegardlessOfImplicitReceiver() {
    assertEquals(
        method("Main.add", 1)
            + "push argument 1\npush constant 1\nadd\npop local 0\n"
            + "push local 0\nreturn\n",
        compile(
            "class Main { method int add(int x) { var int result; "
                + "let result = x + 1; return result; } }"));
  }

  @Test
  void methodReadsFirstFieldViaThisSegment() {
    assertEquals(
        method("Main.read", 0) + "push this 0\nreturn\n",
        compile("class Main { field int x; method int read() { return x; } }"));
  }

  @Test
  void methodWritesToFieldViaThisSegment() {
    assertEquals(
        method("Main.set", 0) + "push argument 1\npop this 0\npush constant 0\nreturn\n",
        compile(
            "class Main { field int x; method void set(int value) { "
                + "let x = value; return; } }"));
  }

  @Test
  void multipleFieldsKeepDeclarationOrder() {
    assertEquals(
        method("Main.last", 0) + "push this 2\nreturn\n",
        compile(
            "class Main { field int a, b; field int c; " + "method int last() { return c; } }"));
  }

  @Test
  void staticVariablesDoNotShiftFieldIndicesInMethods() {
    assertEquals(
        method("Main.sum", 0) + "push static 0\npush this 1\nadd\nreturn\n",
        compile(
            "class Main { static int shared; field int left, right; "
                + "method int sum() { return shared + right; } }"));
  }

  @Test
  void localVariableMayShadowFieldInMethod() {
    assertEquals(
        method("Main.read", 1) + "push constant 3\npop local 0\npush local 0\nreturn\n",
        compile(
            "class Main { field int x; method int read() { var int x; "
                + "let x = 3; return x; } }"));
  }

  @Test
  void methodMayReturnThis() {
    assertEquals(
        method("Main.self", 0) + "push pointer 0\nreturn\n",
        compile("class Main { method Main self() { return this; } }"));
  }

  @Test
  void methodCallsAnotherMethodWithImplicitThis() {
    assertEquals(
        method("Main.run", 0)
            + "push pointer 0\ncall Main.reset 1\npop temp 0\n"
            + "push constant 0\nreturn\n"
            + method("Main.reset", 0)
            + "push constant 0\nreturn\n",
        compile(
            "class Main { method void run() { do reset(); return; } "
                + "method void reset() { return; } }"));
  }

  @Test
  void methodCanCallLaterMethodWithExplicitArguments() {
    assertEquals(
        method("Main.run", 0)
            + "push pointer 0\npush constant 4\ncall Main.double 2\nreturn\n"
            + method("Main.double", 0)
            + "push argument 1\npush constant 2\ncall Math.multiply 2\nreturn\n",
        compile(
            "class Main { method int run() { return double(4); } "
                + "method int double(int n) { return n * 2; } }"));
  }

  @Test
  void methodCallsCurrentClassFunctionWithoutReceiver() {
    assertEquals(
        method("Main.run", 0)
            + "call Main.helper 0\nreturn\n"
            + "function Main.helper 0\npush constant 5\nreturn\n",
        compile(
            "class Main { method int run() { return helper(); } "
                + "function int helper() { return 5; } }"));
  }

  @Test
  void methodCanCallMethodOnArgumentOfCurrentClass() {
    assertEquals(
        method("Main.use", 0)
            + "push argument 1\ncall Main.get 1\nreturn\n"
            + method("Main.get", 0)
            + "push constant 7\nreturn\n",
        compile(
            "class Main { method int use(Main other) { return other.get(); } "
                + "method int get() { return 7; } }"));
  }

  @Test
  void methodCanCallMethodOnLocalObject() {
    assertEquals(
        method("Main.use", 1)
            + "push local 0\ncall Main.get 1\nreturn\n"
            + method("Main.get", 0)
            + "push constant 7\nreturn\n",
        compile(
            "class Main { method int use() { var Main other; "
                + "return other.get(); } method int get() { return 7; } }"));
  }

  @Test
  void fieldObjectReceiverIsLoadedFromThisSegment() {
    assertEquals(
        method("Main.run", 0)
            + "push this 0\ncall Worker.start 1\n"
            + "pop temp 0\npush constant 0\nreturn\n",
        compile(
            "class Main { field Worker task; method void run() { "
                + "do task.start(); return; } }"));
  }

  @Test
  void methodSupportsFieldArrayAssignmentAndRead() {
    assertEquals(
        method("Main.set", 0)
            + "push this 0\npush constant 1\nadd\npush argument 1\n"
            + "pop temp 0\npop pointer 1\npush temp 0\npop that 0\n"
            + "push this 0\npush constant 1\nadd\npop pointer 1\n"
            + "push that 0\nreturn\n",
        compile(
            "class Main { field Array values; method int set(int value) { "
                + "let values[1] = value; return values[1]; } }"));
  }

  @Test
  void methodSupportsConditionalControlFlow() {
    assertEquals(
        method("Main.test", 0)
            + "push constant 0\nnot\nif-goto IF$0_FALSE\n"
            + "push constant 1\nreturn\nlabel IF$0_FALSE\n"
            + "push constant 2\nreturn\n",
        compile("class Main { method int test() { " + "if (false) { return 1; } return 2; } }"));
  }

  @Test
  void constructorWithoutFieldsStillAllocatesZeroWords() {
    assertEquals(
        constructor("Empty.new", 0, 0) + "push pointer 0\nreturn\n",
        compile("class Empty { constructor Empty new() { return this; } }"));
  }

  @Test
  void constructorCountsFieldsAcrossDeclarations() {
    assertEquals(
        constructor("Main.new", 0, 3) + "push pointer 0\nreturn\n",
        compile(
            "class Main { field int x, y; field Array values; "
                + "constructor Main new() { return this; } }"));
  }

  @Test
  void constructorExcludesStaticsFromObjectAllocation() {
    assertEquals(
        constructor("Main.new", 0, 1) + "push pointer 0\nreturn\n",
        compile(
            "class Main { static int a, b; field int x; "
                + "static int c; constructor Main new() { return this; } }"));
  }

  @Test
  void constructorCountsLocalsSeparatelyFromFields() {
    assertEquals(
        constructor("Main.new", 2, 1) + "push constant 5\npop local 1\npush pointer 0\nreturn\n",
        compile(
            "class Main { field int x; constructor Main new() { "
                + "var int a, b; let b = 5; return this; } }"));
  }

  @Test
  void constructorUsesArgumentZeroForFirstExplicitParameter() {
    assertEquals(
        constructor("Main.new", 0, 1) + "push argument 0\npop this 0\npush pointer 0\nreturn\n",
        compile(
            "class Main { field int x; constructor Main new(int x0) { "
                + "let x = x0; return this; } }"));
  }

  @Test
  void constructorUsesSecondExplicitArgumentAtIndexOne() {
    assertEquals(
        constructor("Main.new", 0, 2)
            + "push argument 0\npop this 0\npush argument 1\npop this 1\n"
            + "push pointer 0\nreturn\n",
        compile(
            "class Main { field int x, y; constructor Main new(int a, int b) { "
                + "let x = a; let y = b; return this; } }"));
  }

  @Test
  void constructorCanCallCurrentClassMethodAfterAllocation() {
    assertEquals(
        constructor("Main.new", 0, 0)
            + "push pointer 0\ncall Main.reset 1\npop temp 0\n"
            + "push pointer 0\nreturn\n"
            + method("Main.reset", 0)
            + "push constant 0\nreturn\n",
        compile(
            "class Main { constructor Main new() { do reset(); return this; } "
                + "method void reset() { return; } }"));
  }

  @Test
  void constructorCanCallExternalOsRoutines() {
    assertEquals(
        constructor("Main.new", 0, 0)
            + "push constant 7\ncall Output.printInt 1\npop temp 0\n"
            + "push pointer 0\nreturn\n",
        compile(
            "class Main { constructor Main new() { " + "do Output.printInt(7); return this; } }"));
  }

  @Test
  void staticFunctionCanCallCurrentClassConstructorByClassName() {
    assertEquals(
        "function Main.make 0\ncall Main.new 0\nreturn\n"
            + constructor("Main.new", 0, 0)
            + "push pointer 0\nreturn\n",
        compile(
            "class Main { function Main make() { return Main.new(); } "
                + "constructor Main new() { return this; } }"));
  }

  @Test
  void constructorCanCallAnotherConstructorWithoutReceiver() {
    assertEquals(
        constructor("Main.new", 0, 0)
            + "call Main.another 0\nreturn\n"
            + constructor("Main.another", 0, 0)
            + "push pointer 0\nreturn\n",
        compile(
            "class Main { constructor Main new() { return another(); } "
                + "constructor Main another() { return this; } }"));
  }

  @Test
  void compilesConstructorFunctionAndMethodInDeclarationOrder() {
    assertEquals(
        constructor("Main.new", 0, 1)
            + "push pointer 0\nreturn\n"
            + "function Main.zero 0\npush constant 0\nreturn\n"
            + method("Main.get", 0)
            + "push this 0\nreturn\n",
        compile(
            "class Main { field int value; "
                + "constructor Main new() { return this; } "
                + "function int zero() { return 0; } "
                + "method int get() { return value; } }"));
  }

  @Test
  void argumentAndLocalIndicesResetAfterMethod() {
    assertEquals(
        method("Main.use", 1)
            + "push argument 1\npop local 0\n"
            + "push local 0\nreturn\n"
            + "function Main.functionUse 1\npush argument 0\npop local 0\n"
            + "push local 0\nreturn\n",
        compile(
            "class Main { method int use(int x) { var int y; "
                + "let y = x; return y; } function int functionUse(int x) { "
                + "var int y; let y = x; return y; } }"));
  }

  @Test
  void labelSequenceResetsBetweenMethodAndConstructor() {
    assertEquals(
        method("Main.test", 0)
            + "push constant 0\nnot\nif-goto IF$0_FALSE\nlabel IF$0_FALSE\n"
            + "push constant 0\nreturn\n"
            + constructor("Main.new", 0, 0)
            + "push constant 0\nnot\nif-goto IF$0_FALSE\nlabel IF$0_FALSE\n"
            + "push pointer 0\nreturn\n",
        compile(
            "class Main { method void test() { if (false) {} return; } "
                + "constructor Main new() { if (false) {} return this; } }"));
  }

  @Test
  void rejectsConstructorWithOtherClassReturnTypeAtSourcePosition() {
    GrammarNode root = parse("class Main { constructor Other new() { return this; } }");
    GrammarNode sub = SyntaxTreeNavigator.grammarChildren(root, SyntaxTag.SUBROUTINE_DEC).get(0);
    SourcePosition declared =
        SyntaxTreeNavigator.firstToken(sub.children().get(1)).orElseThrow().position();

    CompilationException error =
        assertThrows(CompilationException.class, () -> new ClassCompiler().compile(root));
    assertTrue(error.description().contains("Constructor must declare its enclosing class 'Main'"));
    assertSame(declared, error.position().orElseThrow());
  }

  @Test
  void rejectsConstructorDeclaringVoidReturnType() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { constructor void new() { return; } }"));
    assertTrue(error.description().contains("Constructor must declare its enclosing class 'Main'"));
  }

  @Test
  void rejectsConstructorDeclaringPrimitiveReturnType() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { constructor int new() { return 0; } }"));
    assertTrue(error.description().contains("Constructor must declare its enclosing class 'Main'"));
  }

  @Test
  void rejectsConstructorWithoutReturnValue() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { constructor Main new() { return; } }"));
    assertTrue(error.description().contains("non-void Jack subroutine must return a value"));
  }

  @Test
  void rejectsMethodWithValueReturnWhenDeclaredVoid() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { method void run() { return 1; } }"));
    assertTrue(error.description().contains("void Jack subroutine cannot return a value"));
  }

  @Test
  void rejectsStaticFunctionCallingMethodWithoutReceiver() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                compile(
                    "class Main { function void run() { do reset(); return; } "
                        + "method void reset() { return; } }"));
    assertTrue(error.description().contains("Cannot access method 'reset'"));
  }

  @Test
  void rejectsQualifiedMethodCallWithoutObjectReceiver() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                compile(
                    "class Main { method void run() { do Main.reset(); return; } "
                        + "method void reset() { return; } }"));
    assertTrue(
        error.description().contains("Cannot call method 'reset' without an object receiver"));
  }

  @Test
  void rejectsFunctionCallOnObjectReceiver() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                compile(
                    "class Main { method int run(Main other) { return other.helper(); } "
                        + "function int helper() { return 3; } }"));
    assertTrue(
        error.description().contains("Cannot call non-method subroutine 'helper' on an object"));
  }

  @Test
  void rejectsDuplicateMethodParameterNames() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { method void run(int a, int a) { return; } }"));
    assertTrue(error.description().contains("duplicate Jack variable 'a'"));
  }

  @Test
  void rejectsMethodLocalNameDuplicatingParameter() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { method void run(int a) { var int a; return; } }"));
    assertTrue(error.description().contains("duplicate Jack variable 'a'"));
  }

  @Test
  void rejectsFieldAccessInsideStaticFunctionEvenWhenClassHasMethods() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                compile(
                    "class Main { field int x; method int get() { return x; } "
                        + "function int bad() { return x; } }"));
    assertTrue(error.description().contains("Cannot access field 'x'"));
  }

  @Test
  void rejectsThisInsideStaticFunctionEvenWhenClassHasConstructor() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                compile(
                    "class Main { constructor Main new() { return this; } "
                        + "function Main bad() { return this; } }"));
    assertTrue(error.description().contains("Cannot access this"));
  }

  @Test
  void rejectsUnsupportedSubroutineKindFromMalformedTree() {
    GrammarNode root = parse("class Main { function int run() { return 1; } }");
    GrammarNode sub = SyntaxTreeNavigator.grammarChildren(root, SyntaxTag.SUBROUTINE_DEC).get(0);
    GrammarNode changedSub = changed(sub, 0, terminal(TokenType.KEYWORD, "procedure", 12));
    GrammarNode changedClass = changed(root, 3, changedSub);

    CompilationException error =
        assertThrows(CompilationException.class, () -> new ClassCompiler().compile(changedClass));
    assertTrue(error.description().contains("Malformed subroutine declaration"));
    assertEquals(new SourcePosition(3, 12), error.position().orElseThrow());
  }

  private static String compile(String source) {
    return new ClassCompiler().compile(parse(source));
  }

  private static GrammarNode parse(String source) {
    return new JackParser().parse(new JackTokenizer().tokenize(source));
  }

  private static String method(String name, int localCount) {
    return "function " + name + " " + localCount + "\npush argument 0\npop pointer 0\n";
  }

  private static String constructor(String name, int localCount, int fieldCount) {
    return "function "
        + name
        + " "
        + localCount
        + "\npush constant "
        + fieldCount
        + "\ncall Memory.alloc 1\npop pointer 0\n";
  }

  private static GrammarNode changed(GrammarNode original, int index, ParseNode replacement) {
    List<ParseNode> children = new ArrayList<>(original.children());
    children.set(index, replacement);
    return new GrammarNode(original.tag(), children);
  }
}
