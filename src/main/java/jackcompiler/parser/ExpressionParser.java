package jackcompiler.parser;

import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Parses Jack expressions into concrete syntax nodes while retaining the original tokens. */
final class ExpressionParser {

  private static final Set<String> BINARY_OPERATORS =
      Set.of("+", "-", "*", "/", "&", "|", "<", ">", "=");
  private static final Set<String> KEYWORD_CONSTANTS = Set.of("true", "false", "null", "this");

  private final TokenCursor cursor;

  ExpressionParser(TokenCursor cursor) {
    this.cursor = Objects.requireNonNull(cursor, "cursor");
  }

  /** Parses a term followed by zero or more binary-operator/term pairs. */
  GrammarNode parseExpression() {
    List<ParseNode> children = new ArrayList<>();
    children.add(parseTerm());

    while (cursor.peek().filter(ExpressionParser::isBinaryOperator).isPresent()) {
      children.add(new TerminalNode(cursor.advance()));
      children.add(parseTerm());
    }

    return new GrammarNode(SyntaxTag.EXPRESSION, children);
  }

  /** Parses an atomic, parenthesized, unary or array-access term. */
  GrammarNode parseTerm() {
    if (cursor.match(TokenType.SYMBOL, "(")) {
      return new GrammarNode(
          SyntaxTag.TERM,
          List.of(
              new TerminalNode(cursor.advance()),
              parseExpression(),
              new TerminalNode(cursor.consume(TokenType.SYMBOL, ")"))));
    }

    if (cursor.match(TokenType.SYMBOL, "-") || cursor.match(TokenType.SYMBOL, "~")) {
      return new GrammarNode(
          SyntaxTag.TERM, List.of(new TerminalNode(cursor.advance()), parseTerm()));
    }

    if (cursor.match(TokenType.IDENTIFIER)) {
      Token identifier = cursor.advance();
      if (cursor.match(TokenType.SYMBOL, "[")) {
        return new GrammarNode(
            SyntaxTag.TERM,
            List.of(
                new TerminalNode(identifier),
                new TerminalNode(cursor.advance()),
                parseExpression(),
                new TerminalNode(cursor.consume(TokenType.SYMBOL, "]"))));
      }
      return new GrammarNode(SyntaxTag.TERM, List.of(new TerminalNode(identifier)));
    }

    if (cursor.peek().filter(ExpressionParser::isAtomicTerm).isEmpty()) {
      throw new SyntaxException("term", cursor.peek(), cursor.position());
    }

    return new GrammarNode(SyntaxTag.TERM, List.of(new TerminalNode(cursor.advance())));
  }

  private static boolean isAtomicTerm(Token token) {
    return switch (token.type()) {
      case INTEGER_CONSTANT, STRING_CONSTANT, IDENTIFIER -> true;
      case KEYWORD -> KEYWORD_CONSTANTS.contains(token.value());
      case SYMBOL -> false;
    };
  }

  private static boolean isBinaryOperator(Token token) {
    return token.type() == TokenType.SYMBOL && BINARY_OPERATORS.contains(token.value());
  }
}
