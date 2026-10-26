package jackcompiler.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.token.TokenizationResult;
import java.util.List;

/** Shared helpers for ExpressionParser unit tests. */
final class ExpressionParserTestSupport {

  private ExpressionParserTestSupport() {}

  static GrammarNode assertGrammar(ParseNode node, SyntaxTag tag, int childCount) {
    assertTrue(node instanceof GrammarNode);
    GrammarNode grammar = (GrammarNode) node;
    assertEquals(tag, grammar.tag());
    assertEquals(childCount, grammar.children().size());
    return grammar;
  }

  static void assertTerminal(ParseNode node, Token expectedToken) {
    assertTrue(node instanceof TerminalNode);
    assertSame(expectedToken, ((TerminalNode) node).token());
  }

  static void assertTerm(GrammarNode term, Token expectedToken) {
    assertEquals(SyntaxTag.TERM, term.tag());
    assertEquals(1, term.children().size());
    ParseNode child = term.children().get(0);
    assertTrue(child instanceof TerminalNode);
    assertSame(expectedToken, ((TerminalNode) child).token());
  }

  static TokenCursor cursor(Token... tokens) {
    List<Token> original = List.of(tokens);
    SourcePosition eof =
        original.isEmpty()
            ? new SourcePosition(1, 1)
            : new SourcePosition(original.get(original.size() - 1).position().line(), 100);
    return new TokenCursor(new TokenizationResult(original, eof));
  }

  static Token token(TokenType type, String value, int line, int column) {
    return new Token(type, value, new SourcePosition(line, column));
  }
}
