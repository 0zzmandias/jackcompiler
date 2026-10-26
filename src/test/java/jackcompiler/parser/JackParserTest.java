package jackcompiler.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.token.TokenizationResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JackParserTest {

  @Test
  void rejectsNullTokenizationResult() {
    JackParser parser = new JackParser();

    assertThrows(NullPointerException.class, () -> parser.parse(null));
  }

  @Test
  void parsesEmptyClassAndPreservesEveryOriginalToken() {
    TokenizationResult result = tokenize("class Empty {}");

    GrammarNode root = new JackParser().parse(result);

    assertEquals(SyntaxTag.CLASS, root.tag());
    assertEquals(4, root.children().size());
    assertPreservesAllTokens(root, result);
  }

  @Test
  void parsesProvidedTokensWithoutInvokingScanner() {
    List<Token> tokens =
        List.of(
            new Token(TokenType.KEYWORD, "class", new SourcePosition(2, 4)),
            new Token(TokenType.IDENTIFIER, "Custom", new SourcePosition(2, 10)),
            new Token(TokenType.SYMBOL, "{", new SourcePosition(2, 17)),
            new Token(TokenType.SYMBOL, "}", new SourcePosition(2, 18)));

    TokenizationResult result = new TokenizationResult(tokens, new SourcePosition(2, 19));

    GrammarNode root = new JackParser().parse(result);

    assertEquals(SyntaxTag.CLASS, root.tag());
    assertPreservesAllTokens(root, result);
  }

  @Test
  void parsesFunctionWithEmptyParameterListAndBody() {
    GrammarNode root = parse("class Empty { function void run() {} }");

    assertEquals(5, root.children().size());
    GrammarNode subroutine = assertGrammar(root.children().get(3), SyntaxTag.SUBROUTINE_DEC, 7);
    assertGrammar(subroutine.children().get(4), SyntaxTag.PARAMETER_LIST, 0);
    GrammarNode body = assertGrammar(subroutine.children().get(6), SyntaxTag.SUBROUTINE_BODY, 3);
    assertGrammar(body.children().get(1), SyntaxTag.STATEMENTS, 0);
  }

  @Test
  void parsesClassDeclarationsSubroutinesAndStatementsTogether() {
    String source =
        "class Demo {"
            + " static int counter;"
            + " field Point origin;"
            + " constructor Demo new(int n, Point start) {"
            + " var int x, y;"
            + " let x = n;"
            + " if (x) { do Output.printInt(x); }"
            + " else { while (x) { let x = x - 1; } }"
            + " return this;"
            + " }"
            + " function void reset() { return; }"
            + " method int size() { return 1; }"
            + "}";

    TokenizationResult result = tokenize(source);
    GrammarNode root = new JackParser().parse(result);

    assertEquals(SyntaxTag.CLASS, root.tag());
    assertEquals(9, root.children().size());
    assertGrammar(root.children().get(3), SyntaxTag.CLASS_VAR_DEC, 4);
    assertGrammar(root.children().get(4), SyntaxTag.CLASS_VAR_DEC, 4);

    GrammarNode constructor = assertGrammar(root.children().get(5), SyntaxTag.SUBROUTINE_DEC, 7);
    assertGrammar(constructor.children().get(4), SyntaxTag.PARAMETER_LIST, 5);
    GrammarNode body = assertGrammar(constructor.children().get(6), SyntaxTag.SUBROUTINE_BODY, 4);
    assertGrammar(body.children().get(1), SyntaxTag.VAR_DEC, 6);

    GrammarNode statements = assertGrammar(body.children().get(2), SyntaxTag.STATEMENTS, 3);
    assertGrammar(statements.children().get(0), SyntaxTag.LET_STATEMENT, 5);
    assertGrammar(statements.children().get(1), SyntaxTag.IF_STATEMENT, 11);
    assertGrammar(statements.children().get(2), SyntaxTag.RETURN_STATEMENT, 3);

    assertGrammar(root.children().get(6), SyntaxTag.SUBROUTINE_DEC, 7);
    assertGrammar(root.children().get(7), SyntaxTag.SUBROUTINE_DEC, 7);
    assertPreservesAllTokens(root, result);
  }

  @Test
  void integratesNestedIfWhileAndSubroutineCalls() {
    String source =
        "class Main {"
            + " function void run() {"
            + " while (true) {"
            + " if (false) { do Output.printInt(1); }"
            + " else { do Output.printInt(2); }"
            + " }"
            + " return;"
            + " }"
            + "}";

    GrammarNode root = parse(source);
    GrammarNode subroutine = assertGrammar(root.children().get(3), SyntaxTag.SUBROUTINE_DEC, 7);
    GrammarNode body = assertGrammar(subroutine.children().get(6), SyntaxTag.SUBROUTINE_BODY, 3);
    GrammarNode statements = assertGrammar(body.children().get(1), SyntaxTag.STATEMENTS, 2);
    GrammarNode loop = assertGrammar(statements.children().get(0), SyntaxTag.WHILE_STATEMENT, 7);
    GrammarNode loopBody = assertGrammar(loop.children().get(5), SyntaxTag.STATEMENTS, 1);
    GrammarNode conditional = assertGrammar(loopBody.children().get(0), SyntaxTag.IF_STATEMENT, 11);
    GrammarNode thenBody = assertGrammar(conditional.children().get(5), SyntaxTag.STATEMENTS, 1);
    GrammarNode elseBody = assertGrammar(conditional.children().get(9), SyntaxTag.STATEMENTS, 1);

    assertGrammar(thenBody.children().get(0), SyntaxTag.DO_STATEMENT, 8);
    assertGrammar(elseBody.children().get(0), SyntaxTag.DO_STATEMENT, 8);
    assertGrammar(statements.children().get(1), SyntaxTag.RETURN_STATEMENT, 2);
  }

  @Test
  void preservesSourcePositionsAndStringContentsAcrossComments() {
    String source =
        "// header\r\n"
            + "  class Main {\n"
            + "    function void run() {\n"
            + "      do Output.printString(\" a<&>b \");\n"
            + "      return;\n"
            + "    }\n"
            + "  } // tail";

    TokenizationResult result = tokenize(source);
    GrammarNode root = new JackParser().parse(result);

    assertEquals(new SourcePosition(2, 3), result.tokens().get(0).position());

    Token string =
        result.tokens().stream()
            .filter(token -> token.type() == TokenType.STRING_CONSTANT)
            .findFirst()
            .orElseThrow();
    assertEquals(" a<&>b ", string.value());
    assertPreservesAllTokens(root, result);
  }

  @Test
  void acceptsSyntacticallyValidReferencesWithoutSemanticResolution() {
    GrammarNode root =
        parse(
            "class Ghost {"
                + " function void absent() {"
                + " do Unknown.call();"
                + " return 1;"
                + " }"
                + "}");

    assertEquals(SyntaxTag.CLASS, root.tag());
    assertEquals(5, root.children().size());
  }

  @Test
  void reusesSameParserForDifferentValidClasses() {
    JackParser parser = new JackParser();

    GrammarNode first = parser.parse(tokenize("class First {}"));
    GrammarNode second = parser.parse(tokenize("class Second { function void f() { return; } }"));

    assertEquals(SyntaxTag.CLASS, first.tag());
    assertEquals(4, first.children().size());
    assertEquals(SyntaxTag.CLASS, second.tag());
    assertEquals(5, second.children().size());
  }

  @Test
  void reparsesSameLexicalResultDeterministically() {
    JackParser parser = new JackParser();
    TokenizationResult result = tokenize("class Main { function int get() { return 3; } }");

    GrammarNode first = parser.parse(result);
    GrammarNode second = parser.parse(result);

    assertEquals(first, second);
    assertNotSame(first, second);
    assertPreservesAllTokens(first, result);
    assertPreservesAllTokens(second, result);
  }

  @Test
  void remainsReusableAfterSyntaxError() {
    JackParser parser = new JackParser();

    assertThrows(
        SyntaxException.class, () -> parser.parse(tokenize("class Broken { function void f() {")));
    GrammarNode valid = parser.parse(tokenize("class Valid {}"));

    assertEquals(SyntaxTag.CLASS, valid.tag());
    assertEquals(4, valid.children().size());
  }

  @Test
  void independentParserInstancesDoNotShareState() {
    TokenizationResult result = tokenize("class Main {}");

    GrammarNode first = new JackParser().parse(result);
    GrammarNode second = new JackParser().parse(result);

    assertEquals(first, second);
    assertNotSame(first, second);
    assertPreservesAllTokens(first, result);
    assertPreservesAllTokens(second, result);
  }

  @Test
  void returnsImmutableConcreteSyntaxTree() {
    GrammarNode root = parse("class Main { field int x; function void run() { return; } }");

    assertThrows(
        UnsupportedOperationException.class, () -> root.children().add(root.children().get(0)));
    assertGrammar(root.children().get(3), SyntaxTag.CLASS_VAR_DEC, 4);
  }

  @Test
  void rejectsEmptySourceAtOriginalEof() {
    assertEofError("", "KEYWORD 'class'");
  }

  @Test
  void rejectsTriviaOnlySourceAtOriginalEof() {
    assertEofError(" // heading\r\n /* empty */ ", "KEYWORD 'class'");
  }

  @Test
  void rejectsWrongInitialKeyword() {
    assertUnexpectedToken("return;", "KEYWORD 'class'", 0);
  }

  @Test
  void requiresClassNameIdentifier() {
    assertUnexpectedToken("class class {}", "token of type IDENTIFIER", 1);
  }

  @Test
  void requiresOpeningClassBrace() {
    assertUnexpectedToken("class Main field int x;", "SYMBOL '{'", 2);
  }

  @Test
  void requiresClosingClassBraceAtEof() {
    assertEofError("class Main {", "SYMBOL '}'");
  }

  @Test
  void reportsEofInsideUnclosedSubroutineBody() {
    assertEofError("class Main { function void run() { return;", "SYMBOL '}'");
  }

  @Test
  void rejectsClassVariableDeclarationAfterSubroutine() {
    assertUnexpectedToken("class Main { function void run() {} field int x; }", "SYMBOL '}'", 10);
  }

  @Test
  void rejectsLocalVariablesDeclaredAfterStatements() {
    assertUnexpectedToken(
        "class Main { function void run() { return; var int x; } }",
        "statement (let, if, while, do or return)",
        11);
  }

  @Test
  void rejectsVoidParameterType() {
    assertUnexpectedToken(
        "class Main { function void run(void x) {} }",
        "type ('int', 'char', 'boolean' or class name)",
        7);
  }

  @Test
  void propagatesIncompleteExpressionFromNestedStatements() {
    assertUnexpectedToken("class Main { function void run() { let x = 1 + ; } }", "term", 14);
  }

  @Test
  void rejectsSecondClassAfterFirstClassEnds() {
    assertUnexpectedToken("class First {} class Second {}", "end of input", 4);
  }

  @Test
  void rejectsTrailingSemicolonAfterCompleteClass() {
    assertUnexpectedToken("class Main {}; ", "end of input", 4);
  }

  @Test
  void rejectsUnexpectedAdditionalClosingBrace() {
    assertUnexpectedToken("class Main {}}", "end of input", 4);
  }

  @Test
  void permitsWhitespaceAndCommentsAfterClassClosingBrace() {
    TokenizationResult result = tokenize("class Main {} // tail\r\n /* trailing */");

    GrammarNode root = new JackParser().parse(result);

    assertEquals(SyntaxTag.CLASS, root.tag());
    assertPreservesAllTokens(root, result);
  }

  @Test
  void reportsOriginalPositionOfTrailingTokenOnAnotherLine() {
    TokenizationResult result = tokenize("class Main {}\n  do run();");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new JackParser().parse(result));

    Token unexpected = result.tokens().get(4);
    assertEquals("end of input", error.expected());
    assertSame(unexpected, error.found().orElseThrow());
    assertEquals(new SourcePosition(2, 3), error.position());
  }

  private static TokenizationResult tokenize(String source) {
    return new JackTokenizer().tokenize(source);
  }

  private static GrammarNode parse(String source) {
    return new JackParser().parse(tokenize(source));
  }

  private static GrammarNode assertGrammar(ParseNode node, SyntaxTag tag, int childCount) {
    assertTrue(node instanceof GrammarNode);
    GrammarNode grammar = (GrammarNode) node;
    assertEquals(tag, grammar.tag());
    assertEquals(childCount, grammar.children().size());
    return grammar;
  }

  private static void assertPreservesAllTokens(GrammarNode root, TokenizationResult result) {
    List<Token> terminals = new ArrayList<>();
    collectTerminals(root, terminals);

    assertEquals(result.tokens().size(), terminals.size());
    for (int i = 0; i < terminals.size(); i++) {
      assertSame(result.tokens().get(i), terminals.get(i));
    }
  }

  private static void collectTerminals(ParseNode node, List<Token> terminals) {
    if (node instanceof TerminalNode terminal) {
      terminals.add(terminal.token());
    } else if (node instanceof GrammarNode grammar) {
      for (ParseNode child : grammar.children()) {
        collectTerminals(child, terminals);
      }
    }
  }

  private static void assertUnexpectedToken(String source, String expected, int index) {
    TokenizationResult result = tokenize(source);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new JackParser().parse(result));

    Token unexpected = result.tokens().get(index);
    assertEquals(expected, error.expected());
    assertSame(unexpected, error.found().orElseThrow());
    assertEquals(unexpected.position(), error.position());
  }

  private static void assertEofError(String source, String expected) {
    TokenizationResult result = tokenize(source);

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> new JackParser().parse(result));

    assertEquals(expected, error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(result.endPosition(), error.position());
  }
}
