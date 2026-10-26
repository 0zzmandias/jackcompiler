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

  /** Parses an atomic, parenthesized, unary, array-access or subroutine-call term. */
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
      if (isSubroutineCall()) {
        return new GrammarNode(SyntaxTag.TERM, parseSubroutineCall());
      }

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

  /** Parses a subroutine call without introducing a nonstandard grammar wrapper. */
  List<ParseNode> parseSubroutineCall() {
    List<ParseNode> children = new ArrayList<>();
    children.add(new TerminalNode(cursor.consume(TokenType.IDENTIFIER)));

    if (cursor.match(TokenType.SYMBOL, ".")) {
      children.add(new TerminalNode(cursor.advance()));
      children.add(new TerminalNode(cursor.consume(TokenType.IDENTIFIER)));
    }

    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "(")));
    children.add(parseExpressionList());
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, ")")));
    return List.copyOf(children);
  }

  /** Parses zero or more comma-separated expressions, leaving the closing ')' unconsumed. */
  GrammarNode parseExpressionList() {
    List<ParseNode> children = new ArrayList<>();
    if (!cursor.match(TokenType.SYMBOL, ")")) {
      children.add(parseExpression());
      while (cursor.match(TokenType.SYMBOL, ",")) {
        children.add(new TerminalNode(cursor.advance()));
        children.add(parseExpression());
      }
    }
    return new GrammarNode(SyntaxTag.EXPRESSION_LIST, children);
  }

  private boolean isSubroutineCall() {
    return cursor
        .peek(1)
        .filter(
            token ->
                token.type() == TokenType.SYMBOL
                    && (token.value().equals("(") || token.value().equals(".")))
        .isPresent();
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
