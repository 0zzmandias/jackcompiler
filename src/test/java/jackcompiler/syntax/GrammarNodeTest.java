package jackcompiler.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrammarNodeTest {

  @Test
  void preservesTagAndOrderedChildrenIncludingNestedProductions() {
    TerminalNode first = terminal(TokenType.KEYWORD, "return", 1);
    GrammarNode expression =
        new GrammarNode(
            SyntaxTag.EXPRESSION, List.of(terminal(TokenType.INTEGER_CONSTANT, "1", 8)));
    TerminalNode last = terminal(TokenType.SYMBOL, ";", 9);

    GrammarNode node =
        new GrammarNode(SyntaxTag.RETURN_STATEMENT, List.of(first, expression, last));

    assertEquals(SyntaxTag.RETURN_STATEMENT, node.tag());
    assertEquals(List.of(first, expression, last), node.children());
  }

  @Test
  void acceptsEmptyChildrenForOptionalProductions() {
    GrammarNode node = new GrammarNode(SyntaxTag.PARAMETER_LIST, List.of());

    assertEquals(List.of(), node.children());
  }

  @Test
  void copiesTheOriginalChildrenList() {
    TerminalNode child = terminal(TokenType.IDENTIFIER, "x", 1);
    List<ParseNode> mutableChildren = new ArrayList<>();
    mutableChildren.add(child);

    GrammarNode node = new GrammarNode(SyntaxTag.TERM, mutableChildren);
    mutableChildren.clear();

    assertEquals(List.of(child), node.children());
  }

  @Test
  void doesNotAllowChangesToItsChildren() {
    GrammarNode node = new GrammarNode(SyntaxTag.STATEMENTS, List.of());

    assertThrows(
        UnsupportedOperationException.class,
        () -> node.children().add(terminal(TokenType.KEYWORD, "return", 1)));
  }

  @Test
  void rejectsNullTagChildrenAndChildElements() {
    TerminalNode validChild = terminal(TokenType.IDENTIFIER, "x", 1);

    assertThrows(NullPointerException.class, () -> new GrammarNode(null, List.of()));
    assertThrows(NullPointerException.class, () -> new GrammarNode(SyntaxTag.TERM, null));
    assertThrows(
        NullPointerException.class,
        () -> new GrammarNode(SyntaxTag.TERM, Arrays.asList(validChild, null)));
  }

  private static TerminalNode terminal(TokenType type, String value, int column) {
    return new TerminalNode(new Token(type, value, new SourcePosition(1, column)));
  }
}
