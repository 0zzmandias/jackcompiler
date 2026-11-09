package jackcompiler.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.parser.JackParser;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.token.SourcePosition;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises the facade through the existing Jack tokenizer and parser, without VM file I/O. */
class JackVmCompilerTest {

  private final JackVmCompiler compiler = new JackVmCompiler();

  @Test
  void rejectsNullTree() {
    assertThrows(NullPointerException.class, () -> compiler.compile(null));
  }

  @Test
  void rejectsNonClassRoot() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compiler.compile(new GrammarNode(SyntaxTag.STATEMENTS, List.of())));
    assertTrue(error.description().contains("Expected a Jack class declaration"));
    assertTrue(error.position().isEmpty());
  }

  @Test
  void rejectsMalformedEmptyClassRoot() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compiler.compile(new GrammarNode(SyntaxTag.CLASS, List.of())));
    assertTrue(error.description().contains("Malformed Jack class declaration"));
  }

  @Test
  void compilesClassWithoutSubroutinesToEmptyText() {
    assertEquals("", compile("class Empty {}"));
  }

  @Test
  void classFieldsAndStaticsProduceNoVmWithoutRoutines() {
    assertEquals("", compile("class Main { static int counter; field int first, second; }"));
  }

  @Test
  void compilesSimpleVoidFunction() {
    assertEquals(
        "function Main.run 0\npush constant 0\nreturn\n",
        compile("class Main { function void run() { return; } }"));
  }

  @Test
  void compilesNonVoidFunction() {
    assertEquals(
        "function Main.answer 0\npush constant 42\nreturn\n",
        compile("class Main { function int answer() { return 42; } }"));
  }

  @Test
  void compilesArithmeticViaExpressionCompiler() {
    assertEquals(
        "function Main.calc 0\npush constant 2\npush constant 3\ncall Math.multiply 2\n"
            + "push constant 1\nadd\nreturn\n",
        compile("class Main { function int calc() { return 2 * 3 + 1; } }"));
  }

  @Test
  void compilesFunctionArgumentsInDeclarationOrder() {
    assertEquals(
        "function Main.add 0\npush argument 0\npush argument 1\nadd\nreturn\n",
        compile("class Main { function int add(int left, int right) { return left + right; } }"));
  }

  @Test
  void computesLocalVariableCountForVmHeader() {
    assertEquals(
        "function Main.run 3\npush constant 9\npop local 2\npush local 2\nreturn\n",
        compile(
            "class Main { function int run() { var int x, y; var char z; "
                + "let z = 9; return z; } }"));
  }

  @Test
  void keepsLocalScopeIndependentOfArguments() {
    assertEquals(
        "function Main.run 1\npush argument 0\npop local 0\npush local 0\nreturn\n",
        compile(
            "class Main { function int run(int arg) { var int local; "
                + "let local = arg; return local; } }"));
  }

  @Test
  void resolvesForwardCurrentClassFunctionCall() {
    assertEquals(
        "function Main.run 0\npush constant 5\ncall Main.helper 1\nreturn\n"
            + "function Main.helper 0\npush argument 0\nreturn\n",
        compile(
            "class Main { function int run() { return helper(5); } "
                + "function int helper(int n) { return n; } }"));
  }

  @Test
  void preservesFunctionDeclarationOrder() {
    assertEquals(
        "function Main.second 0\npush constant 2\nreturn\n"
            + "function Main.first 0\npush constant 1\nreturn\n",
        compile(
            "class Main { function int second() { return 2; } "
                + "function int first() { return 1; } }"));
  }

  @Test
  void compilesStaticVariableAccess() {
    assertEquals(
        "function Main.get 0\npush static 0\nreturn\n",
        compile("class Main { static int count; function int get() { return count; } }"));
  }

  @Test
  void compilesMethodReceiverPrologueAndFieldRead() {
    assertEquals(
        "function Point.getX 0\npush argument 0\npop pointer 0\npush this 0\nreturn\n",
        compile("class Point { field int x; method int getX() { return x; } }"));
  }

  @Test
  void compilesMethodWithImplicitReceiverAndExplicitArgument() {
    assertEquals(
        "function Point.setX 0\npush argument 0\npop pointer 0\n"
            + "push argument 1\npop this 0\npush constant 0\nreturn\n",
        compile(
            "class Point { field int x; method void setX(int value) { "
                + "let x = value; return; } }"));
  }

  @Test
  void compilesConstructorAllocationAndThisReturn() {
    assertEquals(
        "function Point.new 0\npush constant 2\ncall Memory.alloc 1\n"
            + "pop pointer 0\npush pointer 0\nreturn\n",
        compile("class Point { field int x, y; constructor Point new() { return this; } }"));
  }

  @Test
  void constructorParametersDoNotUseImplicitReceiverSlot() {
    assertEquals(
        "function Point.new 0\npush constant 1\ncall Memory.alloc 1\n"
            + "pop pointer 0\npush argument 0\npop this 0\npush pointer 0\nreturn\n",
        compile(
            "class Point { field int x; constructor Point new(int value) { "
                + "let x = value; return this; } }"));
  }

  @Test
  void compilesConstructorWithNoFields() {
    assertEquals(
        "function Empty.new 0\npush constant 0\ncall Memory.alloc 1\n"
            + "pop pointer 0\npush pointer 0\nreturn\n",
        compile("class Empty { constructor Empty new() { return this; } }"));
  }

  @Test
  void compilesImplicitMethodCallWithReceiver() {
    assertEquals(
        "function Main.run 0\npush argument 0\npop pointer 0\npush pointer 0\n"
            + "call Main.reset 1\npop temp 0\npush constant 0\nreturn\n"
            + "function Main.reset 0\npush argument 0\npop pointer 0\n"
            + "push constant 0\nreturn\n",
        compile(
            "class Main { method void run() { do reset(); return; } "
                + "method void reset() { return; } }"));
  }

  @Test
  void compilesCallThroughObjectParameter() {
    assertEquals(
        "function Main.read 0\npush argument 0\npop pointer 0\n"
            + "push argument 1\ncall Main.value 1\nreturn\n"
            + "function Main.value 0\npush argument 0\npop pointer 0\n"
            + "push constant 3\nreturn\n",
        compile(
            "class Main { method int read(Main other) { return other.value(); } "
                + "method int value() { return 3; } }"));
  }

  @Test
  void compilesExternalLibraryCall() {
    assertEquals(
        "function Main.run 0\npush constant 7\ncall Output.printInt 1\n"
            + "pop temp 0\npush constant 0\nreturn\n",
        compile("class Main { function void run() { do Output.printInt(7); return; } }"));
  }

  @Test
  void compilesIfElseWithDeterministicLabels() {
    assertEquals(
        "function Main.run 0\npush constant 0\nnot\nnot\nif-goto IF$0_FALSE\n"
            + "push constant 1\nreturn\ngoto IF$0_END\nlabel IF$0_FALSE\n"
            + "push constant 2\nreturn\nlabel IF$0_END\n",
        compile(
            "class Main { function int run() { if (true) { return 1; } "
                + "else { return 2; } } }"));
  }

  @Test
  void compilesWhileLoopAndLocalAssignments() {
    assertEquals(
        "function Main.run 1\npush constant 0\npop local 0\n"
            + "label WHILE$0_EXP\npush local 0\npush constant 2\nlt\nnot\n"
            + "if-goto WHILE$0_END\npush local 0\npush constant 1\nadd\n"
            + "pop local 0\ngoto WHILE$0_EXP\nlabel WHILE$0_END\n"
            + "push local 0\nreturn\n",
        compile(
            "class Main { function int run() { var int n; let n = 0; "
                + "while (n < 2) { let n = n + 1; } return n; } }"));
  }

  @Test
  void compilesArrayRead() {
    assertEquals(
        "function Main.read 0\npush argument 0\npush constant 1\nadd\n"
            + "pop pointer 1\npush that 0\nreturn\n",
        compile("class Main { function int read(Array array) { return array[1]; } }"));
  }

  @Test
  void compilesArrayWrite() {
    assertEquals(
        "function Main.write 0\npush argument 0\npush constant 2\nadd\n"
            + "push constant 9\npop temp 0\npop pointer 1\npush temp 0\n"
            + "pop that 0\npush constant 0\nreturn\n",
        compile(
            "class Main { function void write(Array array) { " + "let array[2] = 9; return; } }"));
  }

  @Test
  void compilesStringConstantThroughOperatingSystem() {
    assertEquals(
        "function Main.text 0\npush constant 2\ncall String.new 1\n"
            + "push constant 72\ncall String.appendChar 2\n"
            + "push constant 105\ncall String.appendChar 2\nreturn\n",
        compile("class Main { function String text() { return \"Hi\"; } }"));
  }

  @Test
  void resetsSubroutineLabelsAndLocalsAcrossFunctions() {
    assertEquals(
        "function Main.first 1\nlabel WHILE$0_EXP\npush constant 0\nnot\n"
            + "if-goto WHILE$0_END\ngoto WHILE$0_EXP\nlabel WHILE$0_END\n"
            + "push constant 0\nreturn\n"
            + "function Main.second 0\nlabel WHILE$0_EXP\npush constant 0\nnot\n"
            + "if-goto WHILE$0_END\ngoto WHILE$0_EXP\nlabel WHILE$0_END\n"
            + "push constant 0\nreturn\n",
        compile(
            "class Main { function void first() { var int x; "
                + "while (false) {} return; } function void second() { "
                + "while (false) {} return; } }"));
  }

  @Test
  void reportsUndefinedVariableWithOriginalSourcePosition() {
    GrammarNode tree = parse("class Main {\n  function int bad() { return missing; }\n}");
    CompilationException error =
        assertThrows(CompilationException.class, () -> compiler.compile(tree));
    assertTrue(error.description().contains("Undefined Jack variable 'missing'"));
    assertEquals(new SourcePosition(2, 31), error.position().orElseThrow());
  }

  @Test
  void reportsDuplicateSymbolWithPosition() {
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> compile("class Main { static int n; field int n; }"));
    assertTrue(error.description().contains("duplicate Jack variable 'n'"));
    assertTrue(error.position().isPresent());
  }

  @Test
  void reportsDuplicateSubroutineWithPosition() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                compile(
                    "class Main { function void x() { return; } "
                        + "function void x() { return; } }"));
    assertTrue(error.description().contains("Duplicate subroutine 'x'"));
    assertTrue(error.position().isPresent());
  }

  @Test
  void rejectsMisdeclaredConstructor() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { constructor int new() { return 0; } }"));
    assertTrue(error.description().contains("Constructor must declare its enclosing class"));
  }

  @Test
  void rejectsFieldAccessInsideStaticFunction() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { field int x; function int run() { return x; } }"));
    assertTrue(error.description().contains("Cannot access field 'x'"));
  }

  @Test
  void rejectsUnqualifiedMethodCallInsideStaticFunction() {
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
  void rejectsIncorrectNumberOfArguments() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                compile(
                    "class Main { function int run() { return helper(); } "
                        + "function int helper(int x) { return x; } }"));
    assertTrue(error.description().contains("expects 1 argument(s), found 0"));
  }

  @Test
  void rejectsReturnValueFromVoidSubroutine() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> compile("class Main { function void run() { return 1; } }"));
    assertTrue(error.description().contains("void Jack subroutine cannot return a value"));
  }

  @Test
  void sameTreeAlwaysProducesIdenticalVmText() {
    GrammarNode tree = parse("class Main { function int run() { return 9; } }");
    String first = compiler.compile(tree);
    String second = compiler.compile(tree);
    assertEquals(first, second);
  }

  @Test
  void differentFacadeInstancesProduceSameVmForSameTree() {
    GrammarNode tree = parse("class Main { function int run() { return 9; } }");
    assertEquals(compiler.compile(tree), new JackVmCompiler().compile(tree));
  }

  @Test
  void sameFacadeMayCompileIndependentClassesConsecutively() {
    assertEquals(
        "function First.run 0\npush constant 1\nreturn\n",
        compile("class First { function int run() { return 1; } }"));
    assertEquals(
        "function Second.run 0\npush constant 2\nreturn\n",
        compile("class Second { function int run() { return 2; } }"));
  }

  @Test
  void sameFacadeDoesNotRetainFieldCountsAcrossClasses() {
    assertEquals(
        "function Large.new 0\npush constant 3\ncall Memory.alloc 1\n"
            + "pop pointer 0\npush pointer 0\nreturn\n",
        compile(
            "class Large { field int a, b, c; " + "constructor Large new() { return this; } }"));
    assertEquals(
        "function Small.new 0\npush constant 1\ncall Memory.alloc 1\n"
            + "pop pointer 0\npush pointer 0\nreturn\n",
        compile("class Small { field int a; " + "constructor Small new() { return this; } }"));
  }

  @Test
  void failedCompilationDoesNotCorruptNextCompilation() {
    assertThrows(
        CompilationException.class,
        () -> compile("class Main { function int run() { return missing; } }"));
    assertEquals(
        "function Main.run 0\npush constant 5\nreturn\n",
        compile("class Main { function int run() { return 5; } }"));
  }

  @Test
  void vmTextUsesLineFeedsAndTerminatingNewline() {
    String vm = compile("class Main { function void run() { return; } }");
    assertTrue(vm.endsWith("\n"));
    assertFalse(vm.contains("\r"));
    assertFalse(vm.contains("<tokens>"));
    assertFalse(vm.contains("<class>"));
  }

  @Test
  void outputChangesWhenTreeChangesButPreservesOldResult() {
    String first = compile("class Main { function int run() { return 1; } }");
    String second = compile("class Main { function int run() { return 2; } }");
    assertNotEquals(first, second);
    assertEquals("function Main.run 0\npush constant 1\nreturn\n", first);
    assertEquals("function Main.run 0\npush constant 2\nreturn\n", second);
  }

  private String compile(String source) {
    return compiler.compile(parse(source));
  }

  private static GrammarNode parse(String source) {
    return new JackParser().parse(new JackTokenizer().tokenize(source));
  }
}
