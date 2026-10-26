package jackcompiler.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.Token;
import jackcompiler.token.TokenizationResult;

/** Shared fixtures and syntax tree assertions for class structure parser tests. */
final class ClassStructureParserTestSupport {

  private ClassStructureParserTestSupport() {}

  static Fixture fixture(String source) {
    TokenizationResult result = new JackTokenizer().tokenize(source);
    TokenCursor cursor = new TokenCursor(result);
    ExpressionParser expressions = new ExpressionParser(cursor);
    StatementParser statements = new StatementParser(cursor, expressions);
    return new Fixture(result, cursor, new ClassStructureParser(cursor, statements));
  }

  static GrammarNode assertGrammar(ParseNode node, SyntaxTag tag, int childCount) {
    assertTrue(node instanceof GrammarNode);
    GrammarNode grammar = (GrammarNode) node;
    assertEquals(tag, grammar.tag());
    assertEquals(childCount, grammar.children().size());
    return grammar;
  }

  static void assertTerminal(ParseNode node, Token original) {
    assertTrue(node instanceof TerminalNode);
    assertSame(original, ((TerminalNode) node).token());
  }

  record Fixture(TokenizationResult result, TokenCursor cursor, ClassStructureParser parser) {}
}
