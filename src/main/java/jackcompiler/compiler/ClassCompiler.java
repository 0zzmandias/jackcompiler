package jackcompiler.compiler;

import jackcompiler.symbol.SymbolKind;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Coordinates class symbols, subroutine scopes and VM emission for one Jack class. */
final class ClassCompiler {

  /** Compiles a complete Jack class into VM code using fresh state for each invocation. */
  String compile(GrammarNode root) {
    Objects.requireNonNull(root, "root");
    if (root.tag() != SyntaxTag.CLASS) {
      throw CompilationException.atNode("Expected a Jack class declaration.", root);
    }

    List<ParseNode> children = root.children();
    if (children.size() < 4) {
      throw CompilationException.atNode("Malformed Jack class declaration.", root);
    }
    requireKeyword(children.get(0), "class");
    Token className = requireToken(children.get(1), TokenType.IDENTIFIER);
    requireSymbol(children.get(2), "{");
    requireSymbol(children.get(children.size() - 1), "}");

    List<GrammarNode> classVariables = new ArrayList<>();
    List<GrammarNode> subroutines = new ArrayList<>();
    boolean reachedSubroutines = false;
    for (int index = 3; index < children.size() - 1; index++) {
      ParseNode child = children.get(index);
      if (!(child instanceof GrammarNode declaration)) {
        throw CompilationException.atNode("Expected a Jack class member.", child);
      }
      if (declaration.tag() == SyntaxTag.CLASS_VAR_DEC && !reachedSubroutines) {
        classVariables.add(declaration);
      } else if (declaration.tag() == SyntaxTag.SUBROUTINE_DEC) {
        reachedSubroutines = true;
        subroutines.add(declaration);
      } else {
        throw CompilationException.atNode("Unexpected or out-of-order Jack class member.", child);
      }
    }

    SubroutineCatalog catalog = SubroutineCatalog.fromClass(root);
    CompilationContext context = new CompilationContext(className.value());
    for (GrammarNode declaration : classVariables) {
      registerClassVariables(context, declaration);
    }
    for (GrammarNode declaration : subroutines) {
      compileFunction(context, catalog, declaration);
    }
    return context.writer().toVm();
  }

  private void registerClassVariables(CompilationContext context, GrammarNode declaration) {
    List<ParseNode> children = declaration.children();
    if (children.size() < 4 || children.size() % 2 != 0) {
      throw CompilationException.atNode("Malformed Jack class variable declaration.", declaration);
    }
    Token category = requireToken(children.get(0), TokenType.KEYWORD);
    SymbolKind kind =
        switch (category.value()) {
          case "static" -> SymbolKind.STATIC;
          case "field" -> SymbolKind.FIELD;
          default ->
              throw new CompilationException(
                  "Expected Jack class variable kind 'static' or 'field'.", category.position());
        };
    registerVariables(context, declaration, kind);
  }

  private void compileFunction(
      CompilationContext context, SubroutineCatalog catalog, GrammarNode declaration) {
    List<ParseNode> children = declaration.children();
    if (children.size() != 7) {
      throw CompilationException.atNode("Malformed Jack subroutine declaration.", declaration);
    }
    Token kind = requireToken(children.get(0), TokenType.KEYWORD);
    if (!kind.value().equals("function")) {
      if (kind.value().equals("method") || kind.value().equals("constructor")) {
        throw new CompilationException(
            "Jack methods and constructors are not yet supported.", kind.position());
      }
      throw new CompilationException(
          "Unsupported Jack subroutine kind '" + kind.value() + "'.", kind.position());
    }

    String returnType = readType(children.get(1), true);
    Token name = requireToken(children.get(2), TokenType.IDENTIFIER);
    requireSymbol(children.get(3), "(");
    GrammarNode parameters = requireGrammar(children.get(4), SyntaxTag.PARAMETER_LIST);
    requireSymbol(children.get(5), ")");
    GrammarNode body = requireGrammar(children.get(6), SyntaxTag.SUBROUTINE_BODY);

    context.startSubroutine(name.value(), false);
    registerParameters(context, parameters);
    GrammarNode statements = registerLocals(context, body);

    context
        .writer()
        .writeFunction(
            context.qualifiedSubroutineName(), context.symbols().count(SymbolKind.LOCAL));
    new StatementCompiler(context, false, catalog, returnType).compile(statements);
  }

  private void registerParameters(CompilationContext context, GrammarNode parameters) {
    List<ParseNode> children = parameters.children();
    if (children.isEmpty()) {
      return;
    }
    if (children.size() < 2 || (children.size() - 2) % 3 != 0) {
      throw CompilationException.atNode("Malformed Jack parameter list.", parameters);
    }
    for (int index = 0; index < children.size(); index += 3) {
      if (index > 0) {
        requireSymbol(children.get(index - 1), ",");
      }
      String type = readType(children.get(index), false);
      Token name = requireToken(children.get(index + 1), TokenType.IDENTIFIER);
      define(context, name, type, SymbolKind.ARGUMENT);
    }
  }

  private GrammarNode registerLocals(CompilationContext context, GrammarNode body) {
    List<ParseNode> children = body.children();
    if (children.size() < 3) {
      throw CompilationException.atNode("Malformed Jack subroutine body.", body);
    }
    requireSymbol(children.get(0), "{");
    requireSymbol(children.get(children.size() - 1), "}");
    for (int index = 1; index < children.size() - 2; index++) {
      GrammarNode declaration = requireGrammar(children.get(index), SyntaxTag.VAR_DEC);
      registerVariables(context, declaration, SymbolKind.LOCAL);
    }
    return requireGrammar(children.get(children.size() - 2), SyntaxTag.STATEMENTS);
  }

  private void registerVariables(
      CompilationContext context, GrammarNode declaration, SymbolKind kind) {
    List<ParseNode> children = declaration.children();
    if (children.size() < 4 || children.size() % 2 != 0) {
      throw CompilationException.atNode("Malformed Jack variable declaration.", declaration);
    }
    String keyword =
        switch (kind) {
          case STATIC -> "static";
          case FIELD -> "field";
          case LOCAL -> "var";
          case ARGUMENT -> throw new IllegalArgumentException("Parameters use a separate grammar.");
        };
    requireKeyword(children.get(0), keyword);
    String type = readType(children.get(1), false);
    requireSymbol(children.get(children.size() - 1), ";");
    for (int index = 2; index < children.size() - 1; index += 2) {
      if (index > 2) {
        requireSymbol(children.get(index - 1), ",");
      }
      Token name = requireToken(children.get(index), TokenType.IDENTIFIER);
      define(context, name, type, kind);
    }
  }

  private void define(CompilationContext context, Token name, String type, SymbolKind kind) {
    try {
      context.symbols().define(name.value(), type, kind);
    } catch (IllegalArgumentException cause) {
      throw new CompilationException(
          "Invalid or duplicate Jack variable '" + name.value() + "'.", name.position(), cause);
    }
  }

  private static String readType(ParseNode node, boolean allowVoid) {
    if (!(node instanceof TerminalNode terminal)) {
      throw CompilationException.atNode("Expected Jack type token.", node);
    }
    Token token = terminal.token();
    if (token.type() == TokenType.IDENTIFIER) {
      return token.value();
    }
    if (token.type() == TokenType.KEYWORD) {
      if (token.value().equals("int")
          || token.value().equals("char")
          || token.value().equals("boolean")
          || (allowVoid && token.value().equals("void"))) {
        return token.value();
      }
    }
    throw new CompilationException("Invalid Jack type '" + token.value() + "'.", token.position());
  }

  private static GrammarNode requireGrammar(ParseNode node, SyntaxTag tag) {
    try {
      return SyntaxTreeNavigator.requireGrammar(node, tag);
    } catch (IllegalArgumentException cause) {
      throw CompilationException.atNode("Expected Jack grammar node " + tag + ".", node, cause);
    }
  }

  private static Token requireToken(ParseNode node, TokenType type) {
    try {
      return SyntaxTreeNavigator.requireToken(node, type);
    } catch (IllegalArgumentException cause) {
      throw CompilationException.atNode("Expected Jack token " + type + ".", node, cause);
    }
  }

  private static void requireKeyword(ParseNode node, String value) {
    requireValue(node, TokenType.KEYWORD, value);
  }

  private static void requireSymbol(ParseNode node, String value) {
    requireValue(node, TokenType.SYMBOL, value);
  }

  private static void requireValue(ParseNode node, TokenType type, String value) {
    Token token = requireToken(node, type);
    if (!token.value().equals(value)) {
      throw CompilationException.atNode("Expected Jack token '" + value + "'.", node);
    }
  }
}
