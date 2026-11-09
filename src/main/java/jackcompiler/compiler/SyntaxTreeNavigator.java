package jackcompiler.compiler;

import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Structural helpers for reading the existing Jack concrete syntax tree. */
final class SyntaxTreeNavigator {

  private SyntaxTreeNavigator() {}

  /** Requires a grammar node with the expected tag. */
  static GrammarNode requireGrammar(ParseNode node, SyntaxTag expectedTag) {
    Objects.requireNonNull(node, "node");
    Objects.requireNonNull(expectedTag, "expectedTag");

    if (node instanceof GrammarNode grammar && grammar.tag() == expectedTag) {
      return grammar;
    }

    throw new IllegalArgumentException(
        "Expected grammar node " + expectedTag + ", found " + describe(node) + ".");
  }

  /** Reads a direct grammar child at a specific position and checks its tag. */
  static GrammarNode grammarAt(GrammarNode parent, int index, SyntaxTag expectedTag) {
    Objects.requireNonNull(expectedTag, "expectedTag");
    return requireGrammar(childAt(parent, index), expectedTag);
  }

  /** Requires a terminal containing the expected lexical token category. */
  static Token requireToken(ParseNode node, TokenType expectedType) {
    Objects.requireNonNull(node, "node");
    Objects.requireNonNull(expectedType, "expectedType");

    if (node instanceof TerminalNode terminal && terminal.token().type() == expectedType) {
      return terminal.token();
    }

    throw new IllegalArgumentException(
        "Expected terminal " + expectedType + ", found " + describe(node) + ".");
  }

  /** Reads a direct terminal child at a specific position and checks its token category. */
  static Token tokenAt(GrammarNode parent, int index, TokenType expectedType) {
    Objects.requireNonNull(expectedType, "expectedType");
    return requireToken(childAt(parent, index), expectedType);
  }

  /** Reads a direct terminal child, checking both category and exact token text. */
  static Token tokenAt(
      GrammarNode parent, int index, TokenType expectedType, String expectedValue) {
    Objects.requireNonNull(expectedValue, "expectedValue");
    Token token = tokenAt(parent, index, expectedType);

    if (!token.value().equals(expectedValue)) {
      throw new IllegalArgumentException(
          "Expected token value '" + expectedValue + "', found '" + token.value() + "'.");
    }
    return token;
  }

  /** Returns only direct grammar children bearing the given tag, in source order. */
  static List<GrammarNode> grammarChildren(GrammarNode parent, SyntaxTag tag) {
    Objects.requireNonNull(parent, "parent");
    Objects.requireNonNull(tag, "tag");

    return parent.children().stream()
        .filter(child -> child instanceof GrammarNode grammar && grammar.tag() == tag)
        .map(child -> (GrammarNode) child)
        .toList();
  }

  /** Checks a production's exact number of direct children. */
  static void requireChildCount(GrammarNode node, int expectedCount) {
    Objects.requireNonNull(node, "node");
    if (expectedCount < 0) {
      throw new IllegalArgumentException("Expected child count must be non-negative.");
    }
    if (node.children().size() != expectedCount) {
      throw new IllegalArgumentException(
          "Expected "
              + expectedCount
              + " children in "
              + node.tag()
              + ", found "
              + node.children().size()
              + ".");
    }
  }

  /** Returns the leftmost source token, if the subtree contains a terminal. */
  static Optional<Token> firstToken(ParseNode node) {
    Objects.requireNonNull(node, "node");

    if (node instanceof TerminalNode terminal) {
      return Optional.of(terminal.token());
    }

    GrammarNode grammar = (GrammarNode) node;
    for (ParseNode child : grammar.children()) {
      Optional<Token> token = firstToken(child);
      if (token.isPresent()) {
        return token;
      }
    }
    return Optional.empty();
  }

  private static ParseNode childAt(GrammarNode parent, int index) {
    Objects.requireNonNull(parent, "parent");
    if (index < 0 || index >= parent.children().size()) {
      throw new IllegalArgumentException(
          "Child index "
              + index
              + " is outside "
              + parent.tag()
              + " (size "
              + parent.children().size()
              + ").");
    }
    return parent.children().get(index);
  }

  private static String describe(ParseNode node) {
    if (node instanceof GrammarNode grammar) {
      return "grammar " + grammar.tag();
    }
    return "terminal " + ((TerminalNode) node).token().type();
  }
}
