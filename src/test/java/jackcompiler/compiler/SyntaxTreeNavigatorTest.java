package jackcompiler.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.parser.JackParser;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.token.TokenizationResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SyntaxTreeNavigatorTest {

  @Test
  void requiresExpectedGrammarAndPreservesOriginalNode() {
    GrammarNode expression = new GrammarNode(SyntaxTag.EXPRESSION, List.of());

    assertSame(expression, SyntaxTreeNavigator.requireGrammar(expression, SyntaxTag.EXPRESSION));
  }

  @Test
  void rejectsWrongGrammarTagAndTerminalInPlaceOfGrammar() {
    GrammarNode term = new GrammarNode(SyntaxTag.TERM, List.of());
    TerminalNode token = terminal(TokenType.IDENTIFIER, "name", 1);

    assertThrows(
        IllegalArgumentException.class,
        () -> SyntaxTreeNavigator.requireGrammar(term, SyntaxTag.EXPRESSION));
    assertThrows(
        IllegalArgumentException.class,
        () -> SyntaxTreeNavigator.requireGrammar(token, SyntaxTag.TERM));
  }

  @Test
  void readsGrammarChildAtExpectedPosition() {
    GrammarNode parameterList = new GrammarNode(SyntaxTag.PARAMETER_LIST, List.of());
    GrammarNode subroutine =
        new GrammarNode(
            SyntaxTag.SUBROUTINE_DEC, List.of(terminal(TokenType.SYMBOL, "(", 1), parameterList));

    assertSame(
        parameterList, SyntaxTreeNavigator.grammarAt(subroutine, 1, SyntaxTag.PARAMETER_LIST));
  }

  @Test
  void rejectsIncorrectGrammarChildTagAndTerminalChild() {
    GrammarNode term = new GrammarNode(SyntaxTag.TERM, List.of());
    GrammarNode expression =
        new GrammarNode(SyntaxTag.EXPRESSION, List.of(term, terminal(TokenType.SYMBOL, "+", 2)));

    assertThrows(
        IllegalArgumentException.class,
        () -> SyntaxTreeNavigator.grammarAt(expression, 0, SyntaxTag.EXPRESSION));
    assertThrows(
        IllegalArgumentException.class,
        () -> SyntaxTreeNavigator.grammarAt(expression, 1, SyntaxTag.TERM));
  }

  @Test
  void readsOriginalTerminalAndPreservesItsPosition() {
    TerminalNode identifier = terminal(TokenType.IDENTIFIER, "value", 7);
    GrammarNode term = new GrammarNode(SyntaxTag.TERM, List.of(identifier));

    Token actual = SyntaxTreeNavigator.tokenAt(term, 0, TokenType.IDENTIFIER);

    assertSame(identifier.token(), actual);
    assertEquals(new SourcePosition(2, 7), actual.position());
    assertSame(
        identifier.token(), SyntaxTreeNavigator.requireToken(identifier, TokenType.IDENTIFIER));
  }

  @Test
  void rejectsIncorrectTerminalCategoryAndGrammarChild() {
    GrammarNode nested = new GrammarNode(SyntaxTag.TERM, List.of());
    GrammarNode expression =
        new GrammarNode(
            SyntaxTag.EXPRESSION, List.of(terminal(TokenType.INTEGER_CONSTANT, "12", 1), nested));

    assertThrows(
        IllegalArgumentException.class,
        () -> SyntaxTreeNavigator.tokenAt(expression, 0, TokenType.IDENTIFIER));
    assertThrows(
        IllegalArgumentException.class,
        () -> SyntaxTreeNavigator.tokenAt(expression, 1, TokenType.INTEGER_CONSTANT));
    assertThrows(
        IllegalArgumentException.class,
        () -> SyntaxTreeNavigator.requireToken(nested, TokenType.IDENTIFIER));
  }

  @Test
  void checksExactTokenCategoryAndText() {
    TerminalNode symbol = terminal(TokenType.SYMBOL, "{", 1);
    GrammarNode node = new GrammarNode(SyntaxTag.CLASS, List.of(symbol));

    assertSame(symbol.token(), SyntaxTreeNavigator.tokenAt(node, 0, TokenType.SYMBOL, "{"));
  }

  @Test
  void rejectsWrongTokenTextWithoutCoercion() {
    GrammarNode node =
        new GrammarNode(SyntaxTag.CLASS, List.of(terminal(TokenType.KEYWORD, "class", 1)));

    assertThrows(
        IllegalArgumentException.class,
        () -> SyntaxTreeNavigator.tokenAt(node, 0, TokenType.KEYWORD, "Class"));
    assertThrows(
        IllegalArgumentException.class,
        () -> SyntaxTreeNavigator.tokenAt(node, 0, TokenType.KEYWORD, "return"));
  }

  @Test
  void rejectsNegativeAndOutOfRangeChildIndices() {
    GrammarNode root = new GrammarNode(SyntaxTag.CLASS, List.of());

    assertThrows(
        IllegalArgumentException.class,
        () -> SyntaxTreeNavigator.grammarAt(root, -1, SyntaxTag.TERM));
    assertThrows(
        IllegalArgumentException.class,
        () -> SyntaxTreeNavigator.grammarAt(root, 0, SyntaxTag.TERM));
    assertThrows(
        IllegalArgumentException.class,
        () -> SyntaxTreeNavigator.tokenAt(root, -1, TokenType.KEYWORD));
    assertThrows(
        IllegalArgumentException.class,
        () -> SyntaxTreeNavigator.tokenAt(root, 1, TokenType.KEYWORD));
  }

  @Test
  void selectsOnlyDirectGrammarChildrenInSourceOrder() {
    GrammarNode first = new GrammarNode(SyntaxTag.CLASS_VAR_DEC, List.of());
    GrammarNode nested = new GrammarNode(SyntaxTag.CLASS_VAR_DEC, List.of());
    GrammarNode subroutine = new GrammarNode(SyntaxTag.SUBROUTINE_DEC, List.of(nested));
    GrammarNode second = new GrammarNode(SyntaxTag.CLASS_VAR_DEC, List.of());
    GrammarNode root =
        new GrammarNode(
            SyntaxTag.CLASS,
            List.of(first, terminal(TokenType.SYMBOL, "{", 1), subroutine, second));

    assertEquals(
        List.of(first, second), SyntaxTreeNavigator.grammarChildren(root, SyntaxTag.CLASS_VAR_DEC));
    assertEquals(
        List.of(subroutine), SyntaxTreeNavigator.grammarChildren(root, SyntaxTag.SUBROUTINE_DEC));
  }

  @Test
  void returnsUnmodifiableFilteredChildren() {
    GrammarNode child = new GrammarNode(SyntaxTag.VAR_DEC, List.of());
    GrammarNode parent = new GrammarNode(SyntaxTag.SUBROUTINE_BODY, List.of(child));
    List<GrammarNode> selected = SyntaxTreeNavigator.grammarChildren(parent, SyntaxTag.VAR_DEC);

    assertThrows(UnsupportedOperationException.class, () -> selected.add(child));
    assertSame(child, selected.get(0));
  }

  @Test
  void returnsEmptyListWhenNoDirectChildMatches() {
    GrammarNode parent =
        new GrammarNode(
            SyntaxTag.CLASS,
            List.of(
                terminal(TokenType.KEYWORD, "class", 1),
                new GrammarNode(SyntaxTag.SUBROUTINE_DEC, List.of())));

    assertEquals(List.of(), SyntaxTreeNavigator.grammarChildren(parent, SyntaxTag.VAR_DEC));
    assertEquals(
        List.of(),
        SyntaxTreeNavigator.grammarChildren(
            new GrammarNode(SyntaxTag.STATEMENTS, List.of()), SyntaxTag.LET_STATEMENT));
  }

  @Test
  void acceptsExactChildCountIncludingEmptyProductions() {
    GrammarNode empty = new GrammarNode(SyntaxTag.PARAMETER_LIST, List.of());
    GrammarNode term =
        new GrammarNode(SyntaxTag.TERM, List.of(terminal(TokenType.INTEGER_CONSTANT, "1", 1)));

    SyntaxTreeNavigator.requireChildCount(empty, 0);
    SyntaxTreeNavigator.requireChildCount(term, 1);
  }

  @Test
  void rejectsIncorrectAndNegativeExpectedChildCount() {
    GrammarNode term =
        new GrammarNode(SyntaxTag.TERM, List.of(terminal(TokenType.IDENTIFIER, "x", 1)));

    assertThrows(
        IllegalArgumentException.class, () -> SyntaxTreeNavigator.requireChildCount(term, 0));
    assertThrows(
        IllegalArgumentException.class, () -> SyntaxTreeNavigator.requireChildCount(term, 2));
    assertThrows(
        IllegalArgumentException.class, () -> SyntaxTreeNavigator.requireChildCount(term, -1));
  }

  @Test
  void findsLeftmostTokenInNestedTreeAndSkipsEmptyProductions() {
    TerminalNode expected = terminal(TokenType.STRING_CONSTANT, "hi", 4);
    GrammarNode empty = new GrammarNode(SyntaxTag.EXPRESSION_LIST, List.of());
    GrammarNode nested = new GrammarNode(SyntaxTag.TERM, List.of(empty, expected));
    GrammarNode root =
        new GrammarNode(
            SyntaxTag.EXPRESSION,
            List.of(
                new GrammarNode(SyntaxTag.PARAMETER_LIST, List.of()),
                nested,
                terminal(TokenType.SYMBOL, ";", 10)));

    assertSame(expected.token(), SyntaxTreeNavigator.firstToken(root).orElseThrow());
  }

  @Test
  void returnsEmptyWhenSubtreeContainsNoTerminals() {
    GrammarNode nested =
        new GrammarNode(SyntaxTag.EXPRESSION, List.of(new GrammarNode(SyntaxTag.TERM, List.of())));

    assertEquals(Optional.empty(), SyntaxTreeNavigator.firstToken(nested));
    assertEquals(
        Optional.empty(),
        SyntaxTreeNavigator.firstToken(new GrammarNode(SyntaxTag.STATEMENTS, List.of())));
  }

  @Test
  void returnsOriginalTokenForTerminalNode() {
    TerminalNode leaf = terminal(TokenType.IDENTIFIER, "result", 3);

    assertSame(leaf.token(), SyntaxTreeNavigator.firstToken(leaf).orElseThrow());
  }

  @Test
  void rejectsNullNavigationArguments() {
    GrammarNode parent =
        new GrammarNode(SyntaxTag.TERM, List.of(terminal(TokenType.IDENTIFIER, "x", 1)));
    TerminalNode leaf = terminal(TokenType.IDENTIFIER, "x", 1);

    assertThrows(
        NullPointerException.class, () -> SyntaxTreeNavigator.requireGrammar(null, SyntaxTag.TERM));
    assertThrows(
        NullPointerException.class, () -> SyntaxTreeNavigator.requireGrammar(parent, null));
    assertThrows(
        NullPointerException.class, () -> SyntaxTreeNavigator.grammarAt(null, 0, SyntaxTag.TERM));
    assertThrows(NullPointerException.class, () -> SyntaxTreeNavigator.grammarAt(parent, 0, null));
    assertThrows(
        NullPointerException.class,
        () -> SyntaxTreeNavigator.requireToken(null, TokenType.IDENTIFIER));
    assertThrows(NullPointerException.class, () -> SyntaxTreeNavigator.requireToken(leaf, null));
    assertThrows(
        NullPointerException.class,
        () -> SyntaxTreeNavigator.tokenAt(null, 0, TokenType.IDENTIFIER));
    assertThrows(NullPointerException.class, () -> SyntaxTreeNavigator.tokenAt(parent, 0, null));
    assertThrows(
        NullPointerException.class,
        () -> SyntaxTreeNavigator.tokenAt(parent, 0, TokenType.IDENTIFIER, null));
    assertThrows(
        NullPointerException.class,
        () -> SyntaxTreeNavigator.grammarChildren(null, SyntaxTag.TERM));
    assertThrows(
        NullPointerException.class, () -> SyntaxTreeNavigator.grammarChildren(parent, null));
    assertThrows(NullPointerException.class, () -> SyntaxTreeNavigator.requireChildCount(null, 0));
    assertThrows(NullPointerException.class, () -> SyntaxTreeNavigator.firstToken(null));
  }

  @Test
  void navigatesRealParserTreeWithoutReparsing() {
    TokenizationResult lexical =
        new JackTokenizer()
            .tokenize(
                "class Demo { field int x; function void run(int n) {"
                    + " var int y; let y = n; return; } }");
    GrammarNode root = new JackParser().parse(lexical);

    assertSame(root, SyntaxTreeNavigator.requireGrammar(root, SyntaxTag.CLASS));
    assertSame(
        lexical.tokens().get(1),
        SyntaxTreeNavigator.tokenAt(root, 1, TokenType.IDENTIFIER, "Demo"));
    assertEquals(1, SyntaxTreeNavigator.grammarChildren(root, SyntaxTag.CLASS_VAR_DEC).size());

    GrammarNode subroutine =
        SyntaxTreeNavigator.grammarChildren(root, SyntaxTag.SUBROUTINE_DEC).get(0);
    assertEquals("run", SyntaxTreeNavigator.tokenAt(subroutine, 2, TokenType.IDENTIFIER).value());
    GrammarNode parameters = SyntaxTreeNavigator.grammarAt(subroutine, 4, SyntaxTag.PARAMETER_LIST);
    SyntaxTreeNavigator.requireChildCount(parameters, 2);

    GrammarNode body = SyntaxTreeNavigator.grammarAt(subroutine, 6, SyntaxTag.SUBROUTINE_BODY);
    assertEquals(1, SyntaxTreeNavigator.grammarChildren(body, SyntaxTag.VAR_DEC).size());
    GrammarNode statements = SyntaxTreeNavigator.grammarChildren(body, SyntaxTag.STATEMENTS).get(0);
    assertEquals(2, statements.children().size());
    assertSame(lexical.tokens().get(0), SyntaxTreeNavigator.firstToken(root).orElseThrow());
  }

  @Test
  void repeatedNavigationDoesNotChangeTheSourceTree() {
    GrammarNode term =
        new GrammarNode(SyntaxTag.TERM, List.of(terminal(TokenType.IDENTIFIER, "x", 1)));
    GrammarNode parent = new GrammarNode(SyntaxTag.EXPRESSION, List.of(term));

    assertSame(term, SyntaxTreeNavigator.grammarAt(parent, 0, SyntaxTag.TERM));
    assertSame(term, SyntaxTreeNavigator.grammarAt(parent, 0, SyntaxTag.TERM));
    assertSame(parent.children().get(0), term);
    assertSame(
        ((TerminalNode) term.children().get(0)).token(),
        SyntaxTreeNavigator.firstToken(parent).orElseThrow());
  }

  private static TerminalNode terminal(TokenType type, String value, int column) {
    return new TerminalNode(new Token(type, value, new SourcePosition(2, column)));
  }
}
