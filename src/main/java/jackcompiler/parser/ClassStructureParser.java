package jackcompiler.parser;

import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.TokenType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Parses Jack class declarations using the shared token cursor and statement parser. */
final class ClassStructureParser {

  private final TokenCursor cursor;
  private final StatementParser statements;

  ClassStructureParser(TokenCursor cursor, StatementParser statements) {
    this.cursor = Objects.requireNonNull(cursor, "cursor");
    this.statements = Objects.requireNonNull(statements, "statements");
  }

  /** Parses one complete class declaration without consuming any following tokens. */
  GrammarNode parseClass() {
    List<ParseNode> children = new ArrayList<>();
    children.add(new TerminalNode(cursor.consume(TokenType.KEYWORD, "class")));
    children.add(new TerminalNode(cursor.consume(TokenType.IDENTIFIER)));
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "{")));

    while (isClassVariableStart()) {
      children.add(parseClassVarDec());
    }

    while (isSubroutineStart()) {
      children.add(parseSubroutineDec());
    }

    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "}")));
    return new GrammarNode(SyntaxTag.CLASS, children);
  }

  /** Parses a static or field declaration containing one or more variable names. */
  GrammarNode parseClassVarDec() {
    List<ParseNode> children = new ArrayList<>();

    if (!isClassVariableStart()) {
      throw new SyntaxException(
          "class variable declaration ('static' or 'field')", cursor.peek(), cursor.position());
    }

    children.add(new TerminalNode(cursor.advance()));
    children.add(parseType());
    children.add(new TerminalNode(cursor.consume(TokenType.IDENTIFIER)));

    while (cursor.match(TokenType.SYMBOL, ",")) {
      children.add(new TerminalNode(cursor.advance()));
      children.add(new TerminalNode(cursor.consume(TokenType.IDENTIFIER)));
    }

    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, ";")));
    return new GrammarNode(SyntaxTag.CLASS_VAR_DEC, children);
  }

  /** Parses a constructor, function or method declaration. */
  GrammarNode parseSubroutineDec() {
    List<ParseNode> children = new ArrayList<>();

    if (!isSubroutineStart()) {
      throw new SyntaxException(
          "subroutine declaration ('constructor', 'function' or 'method')",
          cursor.peek(),
          cursor.position());
    }

    children.add(new TerminalNode(cursor.advance()));
    children.add(parseReturnType());
    children.add(new TerminalNode(cursor.consume(TokenType.IDENTIFIER)));
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "(")));
    children.add(parseParameterList());
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, ")")));
    children.add(parseSubroutineBody());

    return new GrammarNode(SyntaxTag.SUBROUTINE_DEC, children);
  }

  /** Parses a possibly empty comma-separated sequence of typed parameters. */
  GrammarNode parseParameterList() {
    List<ParseNode> children = new ArrayList<>();

    if (!cursor.match(TokenType.SYMBOL, ")")) {
      children.add(parseType());
      children.add(new TerminalNode(cursor.consume(TokenType.IDENTIFIER)));

      while (cursor.match(TokenType.SYMBOL, ",")) {
        children.add(new TerminalNode(cursor.advance()));
        children.add(parseType());
        children.add(new TerminalNode(cursor.consume(TokenType.IDENTIFIER)));
      }
    }

    return new GrammarNode(SyntaxTag.PARAMETER_LIST, children);
  }

  /** Parses local variable declarations followed by a sequence of statements. */
  GrammarNode parseSubroutineBody() {
    List<ParseNode> children = new ArrayList<>();
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "{")));

    while (cursor.match(TokenType.KEYWORD, "var")) {
      children.add(parseVarDec());
    }

    children.add(statements.parseStatements());
    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, "}")));

    return new GrammarNode(SyntaxTag.SUBROUTINE_BODY, children);
  }

  /** Parses one local variable declaration containing one or more names. */
  GrammarNode parseVarDec() {
    List<ParseNode> children = new ArrayList<>();
    children.add(new TerminalNode(cursor.consume(TokenType.KEYWORD, "var")));
    children.add(parseType());
    children.add(new TerminalNode(cursor.consume(TokenType.IDENTIFIER)));

    while (cursor.match(TokenType.SYMBOL, ",")) {
      children.add(new TerminalNode(cursor.advance()));
      children.add(new TerminalNode(cursor.consume(TokenType.IDENTIFIER)));
    }

    children.add(new TerminalNode(cursor.consume(TokenType.SYMBOL, ";")));
    return new GrammarNode(SyntaxTag.VAR_DEC, children);
  }

  /** Recognizes a primitive Jack type or a class name without creating a wrapper node. */
  private TerminalNode parseType() {
    if (cursor.match(TokenType.IDENTIFIER)
        || cursor.match(TokenType.KEYWORD, "int")
        || cursor.match(TokenType.KEYWORD, "char")
        || cursor.match(TokenType.KEYWORD, "boolean")) {
      return new TerminalNode(cursor.advance());
    }

    throw new SyntaxException(
        "type ('int', 'char', 'boolean' or class name)", cursor.peek(), cursor.position());
  }

  private TerminalNode parseReturnType() {
    if (cursor.match(TokenType.KEYWORD, "void")) {
      return new TerminalNode(cursor.advance());
    }

    return parseType();
  }

  private boolean isClassVariableStart() {
    return cursor.match(TokenType.KEYWORD, "static") || cursor.match(TokenType.KEYWORD, "field");
  }

  private boolean isSubroutineStart() {
    return cursor.match(TokenType.KEYWORD, "constructor")
        || cursor.match(TokenType.KEYWORD, "function")
        || cursor.match(TokenType.KEYWORD, "method");
  }
}
