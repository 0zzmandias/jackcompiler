package jackcompiler.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ParseNodeTest {

  @Test
  void permitsExactlyTheTwoConcreteNodeKinds() {
    assertTrue(ParseNode.class.isSealed());
    assertEquals(
        Set.of(GrammarNode.class, TerminalNode.class),
        Set.of(ParseNode.class.getPermittedSubclasses()));
  }

  @Test
  void grammarAndTerminalNodesImplementTheCommonContract() {
    ParseNode grammar = new GrammarNode(SyntaxTag.STATEMENTS, List.of());
    ParseNode terminal =
        new TerminalNode(new Token(TokenType.KEYWORD, "return", new SourcePosition(1, 1)));

    assertInstanceOf(GrammarNode.class, grammar);
    assertInstanceOf(TerminalNode.class, terminal);
  }
}
