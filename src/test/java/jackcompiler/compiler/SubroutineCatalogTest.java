package jackcompiler.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.parser.JackParser;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubroutineCatalogTest {

  @Test
  void indexesAnEmptyClass() {
    SubroutineCatalog catalog = catalog("class Empty {}");

    assertEquals("Empty", catalog.className());
    assertEquals(0, catalog.size());
    assertTrue(catalog.resolve("anything").isEmpty());
  }

  @Test
  void recognizesConstructorsFunctionsAndMethods() {
    SubroutineCatalog catalog =
        catalog(
            "class Main {"
                + " constructor Main new(int size) { return this; }"
                + " function void reset() { return; }"
                + " method boolean ready() { return true; }"
                + "}");

    assertEquals(3, catalog.size());
    assertEquals(SubroutineCatalog.Kind.CONSTRUCTOR, catalog.resolve("new").orElseThrow().kind());
    assertEquals(SubroutineCatalog.Kind.FUNCTION, catalog.resolve("reset").orElseThrow().kind());
    assertEquals(SubroutineCatalog.Kind.METHOD, catalog.resolve("ready").orElseThrow().kind());
    assertEquals("Main", catalog.resolve("new").orElseThrow().returnType());
    assertEquals("void", catalog.resolve("reset").orElseThrow().returnType());
    assertEquals("boolean", catalog.resolve("ready").orElseThrow().returnType());
  }

  @Test
  void preservesParameterTypesInDeclarationOrder() {
    SubroutineCatalog catalog =
        catalog(
            "class Main {"
                + " function void draw(int x, boolean show, Point point, char marker) { return; }"
                + "}");

    SubroutineCatalog.Signature signature = catalog.resolve("draw").orElseThrow();
    assertEquals(List.of("int", "boolean", "Point", "char"), signature.parameterTypes());
    assertEquals(4, signature.parameterCount());
    assertEquals("void", signature.returnType());
  }

  @Test
  void countsOnlyExplicitMethodParameters() {
    SubroutineCatalog catalog =
        catalog("class Point { method void move(int dx, int dy) { return; } }");

    SubroutineCatalog.Signature signature = catalog.resolve("move").orElseThrow();
    assertTrue(signature.isMethod());
    assertEquals(2, signature.parameterCount());
    assertEquals(List.of("int", "int"), signature.parameterTypes());
  }

  @Test
  void doesNotInventAnImplicitReceiverForFunctionsOrConstructors() {
    SubroutineCatalog catalog =
        catalog(
            "class Main {"
                + " constructor Main new() { return this; }"
                + " function void run() { return; }"
                + " method void stop() { return; }"
                + "}");

    assertFalse(catalog.resolve("new").orElseThrow().isMethod());
    assertFalse(catalog.resolve("run").orElseThrow().isMethod());
    assertTrue(catalog.resolve("stop").orElseThrow().isMethod());
    assertEquals(0, catalog.resolve("new").orElseThrow().parameterCount());
    assertEquals(0, catalog.resolve("run").orElseThrow().parameterCount());
    assertEquals(0, catalog.resolve("stop").orElseThrow().parameterCount());
  }

  @Test
  void resolvesSubroutineDeclaredAfterItsCaller() {
    SubroutineCatalog catalog =
        catalog(
            "class Main {"
                + " function void caller() { do helper(1); return; }"
                + " function void helper(int value) { return; }"
                + "}");

    assertEquals(SubroutineCatalog.Kind.FUNCTION, catalog.resolve("helper").orElseThrow().kind());
    assertEquals(1, catalog.resolve("helper").orElseThrow().parameterCount());
  }

  @Test
  void resolvesMutuallyReferencingSubroutinesIndependentlyOfOrder() {
    SubroutineCatalog catalog =
        catalog(
            "class Main {"
                + " function void first() { do second(); return; }"
                + " function void second() { do first(); return; }"
                + "}");

    assertTrue(catalog.resolve("first").isPresent());
    assertTrue(catalog.resolve("second").isPresent());
    assertEquals(2, catalog.size());
  }

  @Test
  void ignoresClassVariablesAndLocalDeclarations() {
    SubroutineCatalog catalog =
        catalog(
            "class Main {"
                + " field int count;"
                + " static Point origin;"
                + " function void run() { var int local; return; }"
                + "}");

    assertEquals(1, catalog.size());
    assertTrue(catalog.resolve("run").isPresent());
    assertTrue(catalog.resolve("count").isEmpty());
    assertTrue(catalog.resolve("origin").isEmpty());
    assertTrue(catalog.resolve("local").isEmpty());
  }

  @Test
  void distinguishesCaseSensitiveSubroutineNames() {
    SubroutineCatalog catalog =
        catalog("class Main { function void run() {} function void Run() {} }");

    assertEquals(2, catalog.size());
    assertTrue(catalog.resolve("run").isPresent());
    assertTrue(catalog.resolve("Run").isPresent());
    assertTrue(catalog.resolve("RUN").isEmpty());
  }

  @Test
  void isolatesCatalogsFromSeparateClasses() {
    SubroutineCatalog first = catalog("class First { function void a() {} }");
    SubroutineCatalog second = catalog("class Second { function void b() {} }");

    assertNotSame(first, second);
    assertEquals("First", first.className());
    assertEquals("Second", second.className());
    assertTrue(first.resolve("b").isEmpty());
    assertTrue(second.resolve("a").isEmpty());
  }

  @Test
  void leavesTheSourceTreeAndParameterTypesImmutable() {
    GrammarNode root = parse("class Main { function void f(int a) {} }");
    SubroutineCatalog first = SubroutineCatalog.fromClass(root);
    SubroutineCatalog second = SubroutineCatalog.fromClass(root);
    SubroutineCatalog.Signature signature = first.resolve("f").orElseThrow();

    assertEquals(first.resolve("f"), second.resolve("f"));
    assertEquals(5, root.children().size());
    assertThrows(UnsupportedOperationException.class, () -> signature.parameterTypes().add("char"));
  }

  @Test
  void rejectsDuplicateFunctionsInOneClass() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> catalog("class Main { function void run() {} function void run() {} }"));

    assertTrue(error.description().contains("Duplicate subroutine 'run'"));
  }

  @Test
  void rejectsDuplicateNamesAcrossDifferentModalities() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> catalog("class Main { function void tick() {} method void tick() {} }"));

    assertTrue(error.description().contains("Duplicate subroutine 'tick'"));
  }

  @Test
  void rejectsOverloadsWithDifferentParameterTypesAndReturnTypes() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> catalog("class Main { function void f() {} function int f(int n) {} }"));

    assertTrue(error.description().contains("Duplicate subroutine 'f'"));
  }

  @Test
  void reportsTheSecondDuplicateAtItsOriginalSourcePosition() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> catalog("class Main {\n function void run() {}\n method int run(int n) {}\n}"));

    assertEquals(new SourcePosition(3, 13), error.position().orElseThrow());
    assertTrue(error.getMessage().contains("line 3, column 13"));
  }

  @Test
  void rejectsNullAndNonClassRoots() {
    assertThrows(NullPointerException.class, () -> SubroutineCatalog.fromClass(null));
    GrammarNode wrong =
        new GrammarNode(SyntaxTag.TERM, List.of(terminal(TokenType.IDENTIFIER, "x")));
    CompilationException error =
        assertThrows(CompilationException.class, () -> SubroutineCatalog.fromClass(wrong));

    assertEquals(new SourcePosition(2, 1), error.position().orElseThrow());
  }

  @Test
  void rejectsMalformedClassHeader() {
    GrammarNode root =
        new GrammarNode(SyntaxTag.CLASS, List.of(terminal(TokenType.KEYWORD, "class")));

    CompilationException error =
        assertThrows(CompilationException.class, () -> SubroutineCatalog.fromClass(root));
    assertTrue(error.description().contains("Malformed class declaration"));
  }

  @Test
  void rejectsMalformedSubroutineStructure() {
    GrammarNode broken =
        new GrammarNode(SyntaxTag.SUBROUTINE_DEC, List.of(terminal(TokenType.KEYWORD, "function")));

    CompilationException error =
        assertThrows(
            CompilationException.class, () -> SubroutineCatalog.fromClass(classWith(broken)));
    assertTrue(error.description().contains("Malformed subroutine declaration"));
    assertTrue(error.getCause() instanceof IllegalArgumentException);
  }

  @Test
  void rejectsInvalidSubroutineModality() {
    GrammarNode broken = declaration("factory", TokenType.KEYWORD, "void", emptyParameters());

    assertThrows(CompilationException.class, () -> SubroutineCatalog.fromClass(classWith(broken)));
  }

  @Test
  void rejectsInvalidReturnType() {
    GrammarNode broken = declaration("function", TokenType.KEYWORD, "field", emptyParameters());

    assertThrows(CompilationException.class, () -> SubroutineCatalog.fromClass(classWith(broken)));
  }

  @Test
  void rejectsIncompleteParameterLists() {
    GrammarNode incomplete =
        new GrammarNode(SyntaxTag.PARAMETER_LIST, List.of(terminal(TokenType.KEYWORD, "int")));

    assertThrows(
        CompilationException.class,
        () ->
            SubroutineCatalog.fromClass(
                classWith(declaration("function", TokenType.KEYWORD, "void", incomplete))));
  }

  @Test
  void rejectsVoidParameterTypes() {
    GrammarNode parameters =
        new GrammarNode(
            SyntaxTag.PARAMETER_LIST,
            List.of(terminal(TokenType.KEYWORD, "void"), terminal(TokenType.IDENTIFIER, "x")));

    assertThrows(
        CompilationException.class,
        () ->
            SubroutineCatalog.fromClass(
                classWith(declaration("function", TokenType.KEYWORD, "void", parameters))));
  }

  @Test
  void rejectsInvalidParameterSeparators() {
    GrammarNode parameters =
        new GrammarNode(
            SyntaxTag.PARAMETER_LIST,
            List.of(
                terminal(TokenType.KEYWORD, "int"),
                terminal(TokenType.IDENTIFIER, "x"),
                terminal(TokenType.SYMBOL, ";"),
                terminal(TokenType.KEYWORD, "char"),
                terminal(TokenType.IDENTIFIER, "y")));

    assertThrows(
        CompilationException.class,
        () ->
            SubroutineCatalog.fromClass(
                classWith(declaration("function", TokenType.KEYWORD, "void", parameters))));
  }

  @Test
  void rejectsMissingParameterNames() {
    GrammarNode parameters =
        new GrammarNode(
            SyntaxTag.PARAMETER_LIST,
            List.of(terminal(TokenType.KEYWORD, "int"), terminal(TokenType.KEYWORD, "return")));

    assertThrows(
        CompilationException.class,
        () ->
            SubroutineCatalog.fromClass(
                classWith(declaration("function", TokenType.KEYWORD, "void", parameters))));
  }

  @Test
  void rejectsIncorrectBodyTag() {
    GrammarNode good = declaration("method", TokenType.KEYWORD, "void", emptyParameters());
    List<ParseNode> children = new ArrayList<>(good.children());
    children.set(6, new GrammarNode(SyntaxTag.STATEMENTS, List.of()));
    GrammarNode broken = new GrammarNode(SyntaxTag.SUBROUTINE_DEC, children);

    assertThrows(CompilationException.class, () -> SubroutineCatalog.fromClass(classWith(broken)));
  }

  @Test
  void unknownNamesAreNotInventedAndNullLookupsAreRejected() {
    SubroutineCatalog catalog = catalog("class Main { function void known() {} }");

    assertTrue(catalog.resolve("External.method").isEmpty());
    assertTrue(catalog.resolve("missing").isEmpty());
    assertThrows(NullPointerException.class, () -> catalog.resolve(null));
  }

  private static SubroutineCatalog catalog(String source) {
    return SubroutineCatalog.fromClass(parse(source));
  }

  private static GrammarNode parse(String source) {
    return new JackParser().parse(new JackTokenizer().tokenize(source));
  }

  private static GrammarNode emptyParameters() {
    return new GrammarNode(SyntaxTag.PARAMETER_LIST, List.of());
  }

  private static GrammarNode declaration(
      String kind, TokenType returnTypeKind, String returnType, GrammarNode parameters) {
    return new GrammarNode(
        SyntaxTag.SUBROUTINE_DEC,
        List.of(
            terminal(TokenType.KEYWORD, kind),
            terminal(returnTypeKind, returnType),
            terminal(TokenType.IDENTIFIER, "run"),
            terminal(TokenType.SYMBOL, "("),
            parameters,
            terminal(TokenType.SYMBOL, ")"),
            new GrammarNode(SyntaxTag.SUBROUTINE_BODY, List.of())));
  }

  private static GrammarNode classWith(GrammarNode declaration) {
    return new GrammarNode(
        SyntaxTag.CLASS,
        List.of(
            terminal(TokenType.KEYWORD, "class"),
            terminal(TokenType.IDENTIFIER, "Main"),
            terminal(TokenType.SYMBOL, "{"),
            declaration,
            terminal(TokenType.SYMBOL, "}")));
  }

  private static TerminalNode terminal(TokenType type, String value) {
    return new TerminalNode(new Token(type, value, new SourcePosition(2, 1)));
  }
}
