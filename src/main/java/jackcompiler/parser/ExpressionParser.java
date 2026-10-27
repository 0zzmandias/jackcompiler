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
      children.add(parseOp());
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
      return new GrammarNode(SyntaxTag.TERM, List.of(parseUnaryOp(), parseTerm()));
    }

    if (cursor.match(TokenType.IDENTIFIER)) {
      if (isSubroutineCall()) {
        return new GrammarNode(SyntaxTag.TERM, parseSubroutineCall());
      }

      TerminalNode identifier = parseVarName();
      if (cursor.match(TokenType.SYMBOL, "[")) {
        return new GrammarNode(
            SyntaxTag.TERM,
            List.of(
                identifier,
                new TerminalNode(cursor.advance()),
                parseExpression(),
                new TerminalNode(cursor.consume(TokenType.SYMBOL, "]"))));
      }
      return new GrammarNode(SyntaxTag.TERM, List.of(identifier));
    }

    if (cursor.peek().filter(ExpressionParser::isAtomicTerm).isEmpty()) {
      throw new SyntaxException("term", cursor.peek(), cursor.position());
    }

    TerminalNode atomic =
        cursor.match(TokenType.KEYWORD)
            ? parseKeywordConstant()
            : new TerminalNode(cursor.advance());
    return new GrammarNode(SyntaxTag.TERM, List.of(atomic));
  }

  /** Parses a subroutine call without introducing a nonstandard grammar wrapper. */
  List<ParseNode> parseSubroutineCall() {
    List<ParseNode> children = new ArrayList<>();

    boolean qualified = cursor.peek(1).filter(ExpressionParser::isDot).isPresent();
    children.add(qualified ? parseCallQualifier() : parseSubroutineName());

    if (cursor.match(TokenType.SYMBOL, ".")) {
      children.add(new TerminalNode(cursor.advance()));
      children.add(parseSubroutineName());
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

  /** Consumes the op production, retaining the original symbol token. */
  private TerminalNode parseOp() {
    if (cursor.peek().filter(ExpressionParser::isBinaryOperator).isEmpty()) {
      throw new SyntaxException("binary operator", cursor.peek(), cursor.position());
    }
    return new TerminalNode(cursor.advance());
  }

  /** Consumes the unaryOp production, retaining the original symbol token. */
  private TerminalNode parseUnaryOp() {
    if (!cursor.match(TokenType.SYMBOL, "-") && !cursor.match(TokenType.SYMBOL, "~")) {
      throw new SyntaxException("unary operator", cursor.peek(), cursor.position());
    }
    return new TerminalNode(cursor.advance());
  }

  /** Consumes the keywordConstant production, retaining the original keyword token. */
  private TerminalNode parseKeywordConstant() {
    if (cursor.peek().filter(ExpressionParser::isKeywordConstant).isEmpty()) {
      throw new SyntaxException("keyword constant", cursor.peek(), cursor.position());
    }
    return new TerminalNode(cursor.advance());
  }

  /** Consumes varName in a term or array access. */
  private TerminalNode parseVarName() {
    return new TerminalNode(cursor.consume(TokenType.IDENTIFIER));
  }

  /** Consumes subroutineName, either directly or after a qualifier. */
  private TerminalNode parseSubroutineName() {
    return new TerminalNode(cursor.consume(TokenType.IDENTIFIER));
  }

  /** Consumes the syntactically ambiguous className/varName preceding a qualified call. */
  private TerminalNode parseCallQualifier() {
    return new TerminalNode(cursor.consume(TokenType.IDENTIFIER));
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

  private static boolean isDot(Token token) {
    return token.type() == TokenType.SYMBOL && token.value().equals(".");
  }

  private static boolean isAtomicTerm(Token token) {
    return switch (token.type()) {
      case INTEGER_CONSTANT, STRING_CONSTANT, IDENTIFIER -> true;
      case KEYWORD -> isKeywordConstant(token);
      case SYMBOL -> false;
    };
  }

  private static boolean isKeywordConstant(Token token) {
    return token.type() == TokenType.KEYWORD && KEYWORD_CONSTANTS.contains(token.value());
  }

  private static boolean isBinaryOperator(Token token) {
    return token.type() == TokenType.SYMBOL && BINARY_OPERATORS.contains(token.value());
  }
}
