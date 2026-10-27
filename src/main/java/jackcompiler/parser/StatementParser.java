package jackcompiler.parser;

import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.TokenType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Parses Jack statements using the shared token cursor and expression parser. */
final class StatementParser {

  private final TokenCursor cursor;
  private final ExpressionParser expressions;

  StatementParser(TokenCursor cursor, ExpressionParser expressions) {
    this.cursor = Objects.requireNonNull(cursor, "cursor");
    this.expressions = Objects.requireNonNull(expressions, "expressions");
  }

  /** Parses a sequence of statements, leaving the enclosing '}' unconsumed. */
  GrammarNode parseStatements() {
    List<ParseNode> children = new ArrayList<>();

    while (!cursor.isAtEnd() && !cursor.match(TokenType.SYMBOL, "}")) {
      children.add(parseStatement());
    }

    return new GrammarNode(SyntaxTag.STATEMENTS, children);
  }

  /** Selects the statement production without introducing an extra grammar node. */
  private GrammarNode parseStatement() {
    if (cursor.match(TokenType.KEYWORD, "let")) {
      return parseLetStatement();
    }
    if (cursor.match(TokenType.KEYWORD, "if")) {
      return parseIfStatement();
    }
    if (cursor.match(TokenType.KEYWORD, "while")) {
      return parseWhileStatement();
    }
    if (cursor.match(TokenType.KEYWORD, "do")) {
      return parseDoStatement();
    }
    if (cursor.match(TokenType.KEYWORD, "return")) {
      return parseReturnStatement();
    }
    throw new SyntaxException(
        "statement (let, if, while, do or return)", cursor.peek(), cursor.position());
  }

  /** Parses a variable or array-element assignment. */
  GrammarNode parseLetStatement() {
    List<ParseNode> children = new ArrayList<>();
    children.add(new TerminalNode(cursor.consume(TokenType.KEYWORD, "let")));
    children.add(parseVarName());

    if (cursor.match(TokenType.SYMBOL, "[")) {
      children.add(new TerminalNode(cursor.advance()));
      children.add(expressions.parseExpression());
      children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "]")));
    }

    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "=")));
    children.add(expressions.parseExpression());
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, ";")));

    return new GrammarNode(SyntaxTag.LET_STATEMENT, children);
  }

  /** Parses a conditional statement with an optional else block. */
  GrammarNode parseIfStatement() {
    List<ParseNode> children = new ArrayList<>();

    children.add(new TerminalNode(cursor.consume(TokenType.KEYWORD, "if")));
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "(")));
    children.add(expressions.parseExpression());
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, ")")));
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "{")));
    children.add(parseStatements());
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "}")));

    if (cursor.match(TokenType.KEYWORD, "else")) {
      children.add(new TerminalNode(cursor.advance()));
      children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "{")));
      children.add(parseStatements());
      children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "}")));
    }

    return new GrammarNode(SyntaxTag.IF_STATEMENT, children);
  }

  /** Parses a while statement with a required braced body. */
  GrammarNode parseWhileStatement() {
    List<ParseNode> children = new ArrayList<>();

    children.add(new TerminalNode(cursor.consume(TokenType.KEYWORD, "while")));
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "(")));
    children.add(expressions.parseExpression());
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, ")")));
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "{")));
    children.add(parseStatements());
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "}")));

    return new GrammarNode(SyntaxTag.WHILE_STATEMENT, children);
  }

  /** Parses a subroutine-call statement without adding a subroutine-call wrapper. */
  GrammarNode parseDoStatement() {
    List<ParseNode> children = new ArrayList<>();
    children.add(new TerminalNode(cursor.consume(TokenType.KEYWORD, "do")));
    children.addAll(expressions.parseSubroutineCall());
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, ";")));

    return new GrammarNode(SyntaxTag.DO_STATEMENT, children);
  }

  /** Parses a return statement with an optional expression. */
  GrammarNode parseReturnStatement() {
    List<ParseNode> children = new ArrayList<>();
    children.add(new TerminalNode(cursor.consume(TokenType.KEYWORD, "return")));

    if (!cursor.match(TokenType.SYMBOL, ";")) {
      children.add(expressions.parseExpression());
    }

    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, ";")));
    return new GrammarNode(SyntaxTag.RETURN_STATEMENT, children);
  }

  /** Consumes varName in an assignment, retaining the original identifier token. */
  private TerminalNode parseVarName() {
    return new TerminalNode(cursor.consume(TokenType.IDENTIFIER));
  }
}
