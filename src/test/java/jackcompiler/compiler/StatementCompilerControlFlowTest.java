package jackcompiler.compiler;

import static jackcompiler.compiler.ExpressionCompilerTestSupport.catalog;
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

/** Tests deterministic VM control flow for Jack if/else and while statements. */
class StatementCompilerControlFlowTest {

  @Test
  void compilesEmptyIfWithTrueCondition() {
    assertEquals(
        "push constant 0\nnot\nnot\nif-goto IF$0_FALSE\nlabel IF$0_FALSE\n",
        compile("if (true) {}"));
  }

  @Test
  void compilesEmptyIfWithFalseCondition() {
    assertEquals(
        "push constant 0\nnot\nif-goto IF$0_FALSE\nlabel IF$0_FALSE\n", compile("if (false) {}"));
  }

  @Test
  void compilesIfBodyBeforeExitLabel() {
    CompilationContext context = context(false);
    context.symbols().define("n", "int", SymbolKind.LOCAL);
    newCompiler(context).compile(parseStatements("if (true) { let n = 4; }"));
    assertEquals(
        "push constant 0\nnot\nnot\nif-goto IF$0_FALSE\n"
            + "push constant 4\npop local 0\nlabel IF$0_FALSE\n",
        context.writer().toVm());
  }

  @Test
  void compilesIfElseWithBothBranches() {
    CompilationContext context = context(false);
    context.symbols().define("n", "int", SymbolKind.LOCAL);
    newCompiler(context).compile(parseStatements("if (true) { let n = 1; } else { let n = 2; }"));
    assertEquals(
        "push constant 0\nnot\nnot\nif-goto IF$0_FALSE\n"
            + "push constant 1\npop local 0\ngoto IF$0_END\n"
            + "label IF$0_FALSE\npush constant 2\npop local 0\nlabel IF$0_END\n",
        context.writer().toVm());
  }

  @Test
  void compilesIfElseWithEmptyBranches() {
    assertEquals(
        "push constant 0\nnot\nif-goto IF$0_FALSE\ngoto IF$0_END\n"
            + "label IF$0_FALSE\nlabel IF$0_END\n",
        compile("if (false) {} else {}"));
  }

  @Test
  void compilesConditionUsingLocalVariable() {
    CompilationContext context = context(false);
    context.symbols().define("flag", "boolean", SymbolKind.LOCAL);
    newCompiler(context).compile(parseStatements("if (flag) {}"));
    assertEquals(
        "push local 0\nnot\nif-goto IF$0_FALSE\nlabel IF$0_FALSE\n", context.writer().toVm());
  }

  @Test
  void compilesComparisonBeforeConditionalJump() {
    CompilationContext context = context(false);
    context.symbols().define("a", "int", SymbolKind.LOCAL);
    context.symbols().define("b", "int", SymbolKind.LOCAL);
    newCompiler(context).compile(parseStatements("if (a < b) {}"));
    assertEquals(
        "push local 0\npush local 1\nlt\nnot\nif-goto IF$0_FALSE\n" + "label IF$0_FALSE\n",
        context.writer().toVm());
  }

  @Test
  void compilesReturnInsideIfAndReturnAfterIt() {
    CompilationContext context = context(false);
    new StatementCompiler(context, false, null, "int")
        .compile(parseStatements("if (true) { return 3; } return 4;"));
    assertEquals(
        "push constant 0\nnot\nnot\nif-goto IF$0_FALSE\n"
            + "push constant 3\nreturn\nlabel IF$0_FALSE\n"
            + "push constant 4\nreturn\n",
        context.writer().toVm());
  }

  @Test
  void compilesNestedIfWithDistinctLabels() {
    assertEquals(
        "push constant 0\nnot\nnot\nif-goto IF$0_FALSE\n"
            + "push constant 0\nnot\nnot\nif-goto IF$1_FALSE\n"
            + "label IF$1_FALSE\nlabel IF$0_FALSE\n",
        compile("if (true) { if (true) {} }"));
  }

  @Test
  void compilesNestedIfInsideElseBranch() {
    assertEquals(
        "push constant 0\nnot\nif-goto IF$0_FALSE\ngoto IF$0_END\n"
            + "label IF$0_FALSE\n"
            + "push constant 0\nnot\nnot\nif-goto IF$1_FALSE\nlabel IF$1_FALSE\n"
            + "label IF$0_END\n",
        compile("if (false) {} else { if (true) {} }"));
  }

  @Test
  void compilesTwoSequentialIfStatementsWithDifferentIds() {
    assertEquals(
        "push constant 0\nnot\nif-goto IF$0_FALSE\nlabel IF$0_FALSE\n"
            + "push constant 0\nnot\nif-goto IF$1_FALSE\nlabel IF$1_FALSE\n",
        compile("if (false) {} if (false) {}"));
  }

  @Test
  void compilesConditionWithExternalSubroutineCall() {
    assertEquals(
        "call Keyboard.keyPressed 0\nnot\nif-goto IF$0_FALSE\n" + "label IF$0_FALSE\n",
        compile("if (Keyboard.keyPressed()) {}"));
  }

  @Test
  void compilesConditionWithCurrentClassMethod() {
    CompilationContext context = context(true);
    SubroutineCatalog catalog = catalog("class Main { method boolean ready() {} }");
    new StatementCompiler(context, true, catalog, "void")
        .compile(parseStatements("if (ready()) {}"));
    assertEquals(
        "push pointer 0\ncall Main.ready 1\nnot\n" + "if-goto IF$0_FALSE\nlabel IF$0_FALSE\n",
        context.writer().toVm());
  }

  @Test
  void rejectsFieldConditionInsideStaticFunction() {
    CompilationContext context = context(false);
    context.symbols().define("active", "boolean", SymbolKind.FIELD);
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> newCompiler(context).compile(parseStatements("if (active) {}")));
    assertTrue(error.description().contains("Cannot access field 'active'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void compilesEmptyWhileWithTrueCondition() {
    assertEquals(
        "label WHILE$0_EXP\npush constant 0\nnot\nnot\n"
            + "if-goto WHILE$0_END\ngoto WHILE$0_EXP\nlabel WHILE$0_END\n",
        compile("while (true) {}"));
  }

  @Test
  void compilesEmptyWhileWithFalseCondition() {
    assertEquals(
        "label WHILE$0_EXP\npush constant 0\nnot\n"
            + "if-goto WHILE$0_END\ngoto WHILE$0_EXP\nlabel WHILE$0_END\n",
        compile("while (false) {}"));
  }

  @Test
  void compilesWhileBodyAndRepeatsCondition() {
    CompilationContext context = context(false);
    context.symbols().define("n", "int", SymbolKind.LOCAL);
    newCompiler(context).compile(parseStatements("while (n < 3) { let n = n + 1; }"));
    assertEquals(
        "label WHILE$0_EXP\npush local 0\npush constant 3\nlt\nnot\n"
            + "if-goto WHILE$0_END\n"
            + "push local 0\npush constant 1\nadd\npop local 0\n"
            + "goto WHILE$0_EXP\nlabel WHILE$0_END\n",
        context.writer().toVm());
  }

  @Test
  void compilesWhileConditionUsingBooleanVariable() {
    CompilationContext context = context(false);
    context.symbols().define("active", "boolean", SymbolKind.LOCAL);
    newCompiler(context).compile(parseStatements("while (active) {}"));
    assertEquals(
        "label WHILE$0_EXP\npush local 0\nnot\nif-goto WHILE$0_END\n"
            + "goto WHILE$0_EXP\nlabel WHILE$0_END\n",
        context.writer().toVm());
  }

  @Test
  void compilesNestedWhileLoopsWithIndependentLabels() {
    assertEquals(
        "label WHILE$0_EXP\npush constant 0\nnot\n"
            + "if-goto WHILE$0_END\n"
            + "label WHILE$1_EXP\npush constant 0\nnot\n"
            + "if-goto WHILE$1_END\ngoto WHILE$1_EXP\nlabel WHILE$1_END\n"
            + "goto WHILE$0_EXP\nlabel WHILE$0_END\n",
        compile("while (false) { while (false) {} }"));
  }

  @Test
  void compilesIfNestedInsideWhileWithUniqueLabelSequence() {
    assertEquals(
        "label WHILE$0_EXP\npush constant 0\nnot\nif-goto WHILE$0_END\n"
            + "push constant 0\nnot\nif-goto IF$1_FALSE\nlabel IF$1_FALSE\n"
            + "goto WHILE$0_EXP\nlabel WHILE$0_END\n",
        compile("while (false) { if (false) {} }"));
  }

  @Test
  void compilesWhileNestedInsideIfWithUniqueLabelSequence() {
    assertEquals(
        "push constant 0\nnot\nif-goto IF$0_FALSE\n"
            + "label WHILE$1_EXP\npush constant 0\nnot\n"
            + "if-goto WHILE$1_END\ngoto WHILE$1_EXP\nlabel WHILE$1_END\n"
            + "label IF$0_FALSE\n",
        compile("if (false) { while (false) {} }"));
  }

  @Test
  void compilesSequentialIfAndWhileWithSharedCounter() {
    assertEquals(
        "push constant 0\nnot\nif-goto IF$0_FALSE\nlabel IF$0_FALSE\n"
            + "label WHILE$1_EXP\npush constant 0\nnot\n"
            + "if-goto WHILE$1_END\ngoto WHILE$1_EXP\nlabel WHILE$1_END\n",
        compile("if (false) {} while (false) {}"));
  }

  @Test
  void compilesDoStatementInsideWhileAndDiscardsCallResult() {
    assertEquals(
        "label WHILE$0_EXP\npush constant 0\nnot\nif-goto WHILE$0_END\n"
            + "push constant 4\ncall Output.printInt 1\npop temp 0\n"
            + "goto WHILE$0_EXP\nlabel WHILE$0_END\n",
        compile("while (false) { do Output.printInt(4); }"));
  }

  @Test
  void compilesWhileConditionReadingArray() {
    CompilationContext context = context(false);
    context.symbols().define("flags", "Array", SymbolKind.LOCAL);
    newCompiler(context).compile(parseStatements("while (flags[0]) {}"));
    assertEquals(
        "label WHILE$0_EXP\npush local 0\npush constant 0\nadd\n"
            + "pop pointer 1\npush that 0\nnot\nif-goto WHILE$0_END\n"
            + "goto WHILE$0_EXP\nlabel WHILE$0_END\n",
        context.writer().toVm());
  }

  @Test
  void compilesArrayWriteInsideWhile() {
    CompilationContext context = context(false);
    context.symbols().define("values", "Array", SymbolKind.LOCAL);
    newCompiler(context).compile(parseStatements("while (false) { let values[0] = 7; }"));
    assertEquals(
        "label WHILE$0_EXP\npush constant 0\nnot\nif-goto WHILE$0_END\n"
            + "push local 0\npush constant 0\nadd\npush constant 7\n"
            + "pop temp 0\npop pointer 1\npush temp 0\npop that 0\n"
            + "goto WHILE$0_EXP\nlabel WHILE$0_END\n",
        context.writer().toVm());
  }

  @Test
  void producesSameLabelsForIndependentCompilationContexts() {
    CompilationContext first = context(false);
    CompilationContext second = context(false);
    newCompiler(first).compile(parseStatements("if (true) {} while (false) {}"));
    newCompiler(second).compile(parseStatements("if (true) {} while (false) {}"));
    assertEquals(first.writer().toVm(), second.writer().toVm());
  }

  @Test
  void resetsLabelNumbersWhenStartingNewSubroutine() {
    CompilationContext context = context(false);
    newCompiler(context).compile(parseStatements("while (false) {}"));
    context.startSubroutine("another", false);
    newCompiler(context).compile(parseStatements("while (false) {}"));

    String once =
        "label WHILE$0_EXP\npush constant 0\nnot\n"
            + "if-goto WHILE$0_END\ngoto WHILE$0_EXP\nlabel WHILE$0_END\n";
    assertEquals(once + once, context.writer().toVm());
  }

  @Test
  void rejectsIfWrongOpeningKeywordBeforeVmEmission() {
    CompilationContext context = context(false);
    GrammarNode original = firstStatement("if (false) {}");
    GrammarNode malformed = changed(original, 0, terminal(TokenType.KEYWORD, "while", 1));
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> newCompiler(context).compile(statements(malformed)));
    assertTrue(error.description().contains("Expected Jack token 'if'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsIfWrongClosingParenthesisWithOriginalPosition() {
    CompilationContext context = context(false);
    GrammarNode original = firstStatement("if (false) {}");
    GrammarNode malformed = changed(original, 3, symbol("]", 9));
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> newCompiler(context).compile(statements(malformed)));
    assertTrue(error.description().contains("Expected Jack token ')'"));
    assertEquals(new SourcePosition(3, 9), error.position().orElseThrow());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsIfWrongThenBodyGrammar() {
    CompilationContext context = context(false);
    GrammarNode original = firstStatement("if (false) {}");
    GrammarNode malformed = changed(original, 5, term());
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> newCompiler(context).compile(statements(malformed)));
    assertTrue(error.description().contains("Expected Jack grammar node STATEMENTS"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsIfElseWrongKeywordBeforeEmittingVm() {
    CompilationContext context = context(false);
    GrammarNode original = firstStatement("if (false) {} else {}");
    GrammarNode malformed = changed(original, 7, terminal(TokenType.KEYWORD, "then", 20));
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> newCompiler(context).compile(statements(malformed)));
    assertTrue(error.description().contains("Expected Jack token 'else'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsIfElseWrongElseBodyGrammar() {
    CompilationContext context = context(false);
    GrammarNode original = firstStatement("if (false) {} else {}");
    GrammarNode malformed = changed(original, 9, term());
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> newCompiler(context).compile(statements(malformed)));
    assertTrue(error.description().contains("Expected Jack grammar node STATEMENTS"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsIfElseWrongClosingBraceWithoutEmittingVm() {
    CompilationContext context = context(false);
    GrammarNode original = firstStatement("if (false) {} else {}");
    GrammarNode malformed = changed(original, 10, symbol(")", 30));
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> newCompiler(context).compile(statements(malformed)));
    assertTrue(error.description().contains("Expected Jack token '}'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsWhileWrongOpeningKeywordBeforeVmEmission() {
    CompilationContext context = context(false);
    GrammarNode original = firstStatement("while (false) {}");
    GrammarNode malformed = changed(original, 0, terminal(TokenType.KEYWORD, "if", 1));
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> newCompiler(context).compile(statements(malformed)));
    assertTrue(error.description().contains("Expected Jack token 'while'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsWhileWrongClosingParenthesisBeforeVmEmission() {
    CompilationContext context = context(false);
    GrammarNode original = firstStatement("while (false) {}");
    GrammarNode malformed = changed(original, 3, symbol("]", 10));
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> newCompiler(context).compile(statements(malformed)));
    assertTrue(error.description().contains("Expected Jack token ')'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsWhileWrongBodyGrammarBeforeVmEmission() {
    CompilationContext context = context(false);
    GrammarNode original = firstStatement("while (false) {}");
    GrammarNode malformed = changed(original, 5, term());
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> newCompiler(context).compile(statements(malformed)));
    assertTrue(error.description().contains("Expected Jack grammar node STATEMENTS"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsWhileWrongClosingBraceBeforeVmEmission() {
    CompilationContext context = context(false);
    GrammarNode original = firstStatement("while (false) {}");
    GrammarNode malformed = changed(original, 6, symbol(")", 20));
    CompilationException error =
        assertThrows(
            CompilationException.class, () -> newCompiler(context).compile(statements(malformed)));
    assertTrue(error.description().contains("Expected Jack token '}'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void reportsUndefinedVariableInsideIfConditionWithOriginalPosition() {
    CompilationContext context = context(false);
    GrammarNode statements = parseStatements("if (missing) {}");
    GrammarNode ifStatement = (GrammarNode) statements.children().get(0);
    GrammarNode condition = (GrammarNode) ifStatement.children().get(2);
    SourcePosition original = SyntaxTreeNavigator.firstToken(condition).orElseThrow().position();

    CompilationException error =
        assertThrows(CompilationException.class, () -> newCompiler(context).compile(statements));
    assertTrue(error.description().contains("Undefined Jack variable 'missing'"));
    assertSame(original, error.position().orElseThrow());
  }

  @Test
  void rejectsInvalidReturnInConditionalBranch() {
    CompilationContext context = context(false);
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> newCompiler(context).compile(parseStatements("if (true) { return 2; }")));
    assertTrue(error.description().contains("void Jack subroutine cannot return a value"));
  }

  private static String compile(String source) {
    CompilationContext context = context(false);
    newCompiler(context).compile(parseStatements(source));
    return context.writer().toVm();
  }

  private static StatementCompiler newCompiler(CompilationContext context) {
    return new StatementCompiler(context, false, null, "void");
  }

  private static CompilationContext context(boolean isMethod) {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", isMethod);
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

  private static GrammarNode firstStatement(String source) {
    return (GrammarNode) parseStatements(source).children().get(0);
  }

  private static GrammarNode changed(GrammarNode original, int index, ParseNode replacement) {
    List<ParseNode> children = new ArrayList<>(original.children());
    children.set(index, replacement);
    return new GrammarNode(original.tag(), children);
  }

  private static GrammarNode statements(ParseNode... children) {
    return new GrammarNode(SyntaxTag.STATEMENTS, List.of(children));
  }
}
