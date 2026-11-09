package jackcompiler.compiler;

import static jackcompiler.compiler.ExpressionCompilerTestSupport.catalog;
import static jackcompiler.compiler.ExpressionCompilerTestSupport.compile;
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
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpressionCompilerSubroutineCallTest {

  @Test
  void rejectsCatalogFromAnotherClass() {
    CompilationContext context = new CompilationContext("Main");
    SubroutineCatalog other = catalog("class Other { function void f() {} }");
    assertThrows(
        IllegalArgumentException.class, () -> new ExpressionCompiler(context, true, other));
  }

  @Test
  void rejectsNullContextWithCatalog() {
    SubroutineCatalog catalog = catalog("class Main { function void f() {} }");
    assertThrows(NullPointerException.class, () -> new ExpressionCompiler(null, true, catalog));
  }

  @Test
  void compilesExternalClassCallWithoutRequiringItsSource() {
    assertEquals(
        "push constant 2\npush constant 3\ncall Math.multiply 2\n", compile("Math.multiply(2, 3)"));
  }

  @Test
  void compilesExternalCallWithoutParameters() {
    assertEquals("call Keyboard.readChar 0\n", compile("Keyboard.readChar()"));
  }

  @Test
  void compilesNestedExternalCallsInArgumentOrder() {
    assertEquals(
        "push constant 2\npush constant 3\ncall Math.multiply 2\ncall Output.printInt 1\n",
        compile("Output.printInt(Math.multiply(2, 3))"));
  }

  @Test
  void compilesCallsInsideExistingExpressions() {
    assertEquals(
        "push constant 1\npush constant 2\npush constant 3\ncall Math.multiply 2\nadd\n",
        compile("1 + Math.multiply(2, 3)"));
  }

  @Test
  void compilesCallsWithVariableAndStringArguments() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    context.symbols().define("value", "int", SymbolKind.LOCAL);
    new ExpressionCompiler(context, false)
        .compile(parseExpression("Screen.drawString(value, \"A\")"));
    assertEquals(
        "push local 0\npush constant 1\ncall String.new 1\n"
            + "push constant 65\ncall String.appendChar 2\ncall Screen.drawString 2\n",
        context.writer().toVm());
  }

  @Test
  void compilesForwardLocalFunctionCallUsingCatalog() {
    SubroutineCatalog catalog =
        catalog(
            "class Main {"
                + " function int run() { return helper(4); }"
                + " function int helper(int x) { return x; }"
                + " }");
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    new ExpressionCompiler(context, false, catalog).compile(parseExpression("helper(4)"));
    assertEquals("push constant 4\ncall Main.helper 1\n", context.writer().toVm());
  }

  @Test
  void compilesLocalMethodWithImplicitReceiver() {
    SubroutineCatalog catalog = catalog("class Main { method void move(int dx, int dy) {} }");
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", true);
    new ExpressionCompiler(context, true, catalog).compile(parseExpression("move(2, 3)"));
    assertEquals(
        "push pointer 0\npush constant 2\npush constant 3\ncall Main.move 3\n",
        context.writer().toVm());
  }

  @Test
  void compilesLocalMethodCallInsideConstructor() {
    SubroutineCatalog catalog = catalog("class Main { method void reset() {} }");
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("new", false);
    new ExpressionCompiler(context, true, catalog).compile(parseExpression("reset()"));
    assertEquals("push pointer 0\ncall Main.reset 1\n", context.writer().toVm());
  }

  @Test
  void localFunctionCalledFromMethodDoesNotReceiveThis() {
    SubroutineCatalog catalog = catalog("class Main { function int size(int x) {} }");
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", true);
    new ExpressionCompiler(context, true, catalog).compile(parseExpression("size(9)"));
    assertEquals("push constant 9\ncall Main.size 1\n", context.writer().toVm());
  }

  @Test
  void localConstructorCallDoesNotReceiveImplicitThis() {
    SubroutineCatalog catalog = catalog("class Main { constructor Main new(int n) {} }");
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    new ExpressionCompiler(context, false, catalog).compile(parseExpression("new(2)"));
    assertEquals("push constant 2\ncall Main.new 1\n", context.writer().toVm());
  }

  @Test
  void rejectsUnqualifiedCallWithoutCatalog() {
    CompilationContext context = new CompilationContext("Main");
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(context).compile(parseExpression("helper()")));
    assertTrue(error.description().contains("catalog is required"));
    assertTrue(error.position().isPresent());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsUndefinedUnqualifiedCallWithOriginalPosition() {
    SubroutineCatalog catalog = catalog("class Main { function void known() {} }");
    CompilationContext context = new CompilationContext("Main");
    GrammarNode expression = parseExpression("missing(1)");
    SourcePosition position = SyntaxTreeNavigator.firstToken(expression).orElseThrow().position();
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(context, true, catalog).compile(expression));
    assertTrue(error.description().contains("Undefined subroutine 'missing'"));
    assertSame(position, error.position().orElseThrow());
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsWrongArityForLocalFunctionBeforeWritingVm() {
    SubroutineCatalog catalog = catalog("class Main { function void f(int a, int b) {} }");
    CompilationContext context = new CompilationContext("Main");
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(context, true, catalog).compile(parseExpression("f(1)")));
    assertTrue(error.description().contains("expects 2 argument(s), found 1"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsLocalMethodCallInStaticFunction() {
    SubroutineCatalog catalog = catalog("class Main { method void move() {} }");
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                new ExpressionCompiler(context, false, catalog).compile(parseExpression("move()")));
    assertTrue(error.description().contains("Cannot access method 'move'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void compilesQualifiedCurrentClassFunctionWithValidatedArguments() {
    SubroutineCatalog catalog = catalog("class Main { function void f(int x, int y) {} }");
    CompilationContext context = new CompilationContext("Main");
    new ExpressionCompiler(context, false, catalog).compile(parseExpression("Main.f(3, 4)"));
    assertEquals("push constant 3\npush constant 4\ncall Main.f 2\n", context.writer().toVm());
  }

  @Test
  void compilesQualifiedCurrentClassConstructorWithoutReceiver() {
    SubroutineCatalog catalog = catalog("class Main { constructor Main new() {} }");
    CompilationContext context = new CompilationContext("Main");
    new ExpressionCompiler(context, false, catalog).compile(parseExpression("Main.new()"));
    assertEquals("call Main.new 0\n", context.writer().toVm());
  }

  @Test
  void rejectsQualifiedCurrentClassMethodWithoutObject() {
    SubroutineCatalog catalog = catalog("class Main { method void move() {} }");
    CompilationContext context = new CompilationContext("Main");
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                new ExpressionCompiler(context, true, catalog)
                    .compile(parseExpression("Main.move()")));
    assertTrue(error.description().contains("without an object receiver"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsQualifiedCurrentClassUnknownSubroutine() {
    SubroutineCatalog catalog = catalog("class Main { function void known() {} }");
    CompilationContext context = new CompilationContext("Main");
    assertThrows(
        CompilationException.class,
        () ->
            new ExpressionCompiler(context, false, catalog)
                .compile(parseExpression("Main.missing()")));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsQualifiedCurrentClassWrongArity() {
    SubroutineCatalog catalog = catalog("class Main { function void f(int x) {} }");
    CompilationContext context = new CompilationContext("Main");
    assertThrows(
        CompilationException.class,
        () -> new ExpressionCompiler(context, false, catalog).compile(parseExpression("Main.f()")));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsQualifiedCurrentClassCallWithoutCatalog() {
    CompilationContext context = new CompilationContext("Main");
    assertThrows(
        CompilationException.class,
        () -> new ExpressionCompiler(context).compile(parseExpression("Main.f()")));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void compilesLocalObjectReceiverBeforeExplicitArguments() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    context.symbols().define("point", "Point", SymbolKind.LOCAL);
    new ExpressionCompiler(context, false).compile(parseExpression("point.move(2, 3)"));
    assertEquals(
        "push local 0\npush constant 2\npush constant 3\ncall Point.move 3\n",
        context.writer().toVm());
  }

  @Test
  void compilesStaticVariableAsObjectReceiver() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("instance", "Printer", SymbolKind.STATIC);
    new ExpressionCompiler(context, false).compile(parseExpression("instance.print(4)"));
    assertEquals("push static 0\npush constant 4\ncall Printer.print 2\n", context.writer().toVm());
  }

  @Test
  void compilesArgumentVariableAsObjectReceiver() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", true);
    context.symbols().define("target", "Point", SymbolKind.ARGUMENT);
    new ExpressionCompiler(context, true).compile(parseExpression("target.reset()"));
    assertEquals("push argument 1\ncall Point.reset 1\n", context.writer().toVm());
  }

  @Test
  void compilesFieldObjectReceiverInsideMethod() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("target", "Point", SymbolKind.FIELD);
    context.startSubroutine("run", true);
    new ExpressionCompiler(context, true).compile(parseExpression("target.reset()"));
    assertEquals("push this 0\ncall Point.reset 1\n", context.writer().toVm());
  }

  @Test
  void rejectsFieldObjectReceiverInsideStaticFunction() {
    CompilationContext context = new CompilationContext("Main");
    context.symbols().define("target", "Point", SymbolKind.FIELD);
    context.startSubroutine("run", false);
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                new ExpressionCompiler(context, false).compile(parseExpression("target.reset()")));
    assertTrue(error.description().contains("Cannot access field 'target'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void resolvesObjectWithCurrentClassTypeAgainstCatalog() {
    SubroutineCatalog catalog = catalog("class Main { method void tick(int value) {} }");
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    context.symbols().define("other", "Main", SymbolKind.LOCAL);
    new ExpressionCompiler(context, false, catalog).compile(parseExpression("other.tick(9)"));
    assertEquals("push local 0\npush constant 9\ncall Main.tick 2\n", context.writer().toVm());
  }

  @Test
  void rejectsObjectCallToCurrentClassFunction() {
    SubroutineCatalog catalog = catalog("class Main { function void reset() {} }");
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    context.symbols().define("other", "Main", SymbolKind.LOCAL);
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () ->
                new ExpressionCompiler(context, false, catalog)
                    .compile(parseExpression("other.reset()")));
    assertTrue(error.description().contains("Cannot call non-method subroutine"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsObjectCallToUnknownCurrentClassMethod() {
    SubroutineCatalog catalog = catalog("class Main { method void known() {} }");
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    context.symbols().define("other", "Main", SymbolKind.LOCAL);
    assertThrows(
        CompilationException.class,
        () ->
            new ExpressionCompiler(context, false, catalog)
                .compile(parseExpression("other.missing()")));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsWrongArityForObjectCallToCurrentClassMethod() {
    SubroutineCatalog catalog = catalog("class Main { method void tick(int n) {} }");
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    context.symbols().define("other", "Main", SymbolKind.LOCAL);
    assertThrows(
        CompilationException.class,
        () ->
            new ExpressionCompiler(context, false, catalog)
                .compile(parseExpression("other.tick()")));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void permitsUnknownExternalClassAndObjectSignatures() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    context.symbols().define("external", "External", SymbolKind.LOCAL);
    ExpressionCompiler compiler = new ExpressionCompiler(context, false);
    compiler.compile(parseExpression("External.create(1, 2, 3)"));
    compiler.compile(parseExpression("external.update()"));
    assertEquals(
        "push constant 1\npush constant 2\npush constant 3\ncall External.create 3\n"
            + "push local 0\ncall External.update 1\n",
        context.writer().toVm());
  }

  @Test
  void rejectsPrimitiveObjectReceiver() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    context.symbols().define("number", "int", SymbolKind.LOCAL);
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new ExpressionCompiler(context, false).compile(parseExpression("number.test()")));
    assertTrue(error.description().contains("primitive variable 'number'"));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void shadowsClassQualifierWithVariableInCurrentScope() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    context.symbols().define("Math", "Calculator", SymbolKind.LOCAL);
    new ExpressionCompiler(context, false).compile(parseExpression("Math.add(1)"));
    assertEquals("push local 0\npush constant 1\ncall Calculator.add 2\n", context.writer().toVm());
  }

  @Test
  void supportsCaseSensitiveClassQualifiers() {
    assertEquals("call main.run 0\n", compile("main.run()"));
  }

  @Test
  void sharesCallCompilationWithFutureDoStatement() {
    CompilationContext context = new CompilationContext("Main");
    GrammarNode expression = parseExpression("Output.printInt(7)");
    GrammarNode term = SyntaxTreeNavigator.grammarAt(expression, 0, SyntaxTag.TERM);
    new ExpressionCompiler(context, false).compileCall(term.children());
    assertEquals("push constant 7\ncall Output.printInt 1\n", context.writer().toVm());
  }

  @Test
  void rejectsNullAndMalformedDirectCallNodes() {
    ExpressionCompiler compiler = new ExpressionCompiler(new CompilationContext("Main"));
    assertThrows(NullPointerException.class, () -> compiler.compileCall(null));
    assertThrows(CompilationException.class, () -> compiler.compileCall(List.of()));
    assertThrows(
        CompilationException.class,
        () -> compiler.compileCall(List.of(terminal(TokenType.IDENTIFIER, "f", 1))));
  }

  @Test
  void rejectsMalformedCallDelimitersBeforeWritingVm() {
    CompilationContext context = new CompilationContext("Main");
    ExpressionCompiler compiler = new ExpressionCompiler(context);
    assertThrows(
        CompilationException.class,
        () ->
            compiler.compileCall(
                List.of(
                    terminal(TokenType.IDENTIFIER, "f", 1),
                    symbol("[", 2),
                    new GrammarNode(SyntaxTag.EXPRESSION_LIST, List.of()),
                    symbol(")", 3))));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsMalformedArgumentListWithMissingComma() {
    CompilationContext context = new CompilationContext("Main");
    ExpressionCompiler compiler = new ExpressionCompiler(context);
    GrammarNode args =
        new GrammarNode(
            SyntaxTag.EXPRESSION_LIST,
            List.of(expression(term(integer("1", 2))), expression(term(integer("2", 4)))));
    assertThrows(
        CompilationException.class,
        () ->
            compiler.compileCall(
                List.of(
                    terminal(TokenType.IDENTIFIER, "f", 1), symbol("(", 2), args, symbol(")", 5))));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsMalformedArgumentListWithWrongSeparator() {
    CompilationContext context = new CompilationContext("Main");
    ExpressionCompiler compiler = new ExpressionCompiler(context);
    GrammarNode args =
        new GrammarNode(
            SyntaxTag.EXPRESSION_LIST,
            List.of(
                expression(term(integer("1", 2))),
                symbol(";", 3),
                expression(term(integer("2", 4)))));
    assertThrows(
        CompilationException.class,
        () ->
            compiler.compileCall(
                List.of(
                    terminal(TokenType.IDENTIFIER, "f", 1), symbol("(", 2), args, symbol(")", 5))));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsMalformedArgumentListWithWrongExpressionTag() {
    CompilationContext context = new CompilationContext("Main");
    ExpressionCompiler compiler = new ExpressionCompiler(context);
    GrammarNode args = new GrammarNode(SyntaxTag.EXPRESSION_LIST, List.of(term(integer("1", 3))));
    assertThrows(
        CompilationException.class,
        () ->
            compiler.compileCall(
                List.of(
                    terminal(TokenType.IDENTIFIER, "f", 1), symbol("(", 2), args, symbol(")", 5))));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void rejectsMalformedQualifiedCallShape() {
    CompilationContext context = new CompilationContext("Main");
    ExpressionCompiler compiler = new ExpressionCompiler(context);
    assertThrows(
        CompilationException.class,
        () ->
            compiler.compileCall(
                List.of(
                    terminal(TokenType.IDENTIFIER, "Math", 1),
                    symbol(";", 5),
                    terminal(TokenType.IDENTIFIER, "abs", 6),
                    symbol("(", 9),
                    new GrammarNode(SyntaxTag.EXPRESSION_LIST, List.of()),
                    symbol(")", 10))));
    assertEquals("", context.writer().toVm());
  }
}
