package jackcompiler.compiler;

import jackcompiler.symbol.Symbol;
import jackcompiler.symbol.SymbolKind;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.vm.VmArithmetic;
import jackcompiler.vm.VmSegment;
import java.util.List;
import java.util.Objects;

/** Translates Jack statements using the shared compilation context and expression compiler. */
final class StatementCompiler {

  private final CompilationContext context;
  private final ExpressionCompiler expressions;
  private final boolean instanceAccessAllowed;
  private final boolean returnsVoid;

  /** Configures the current subroutine's instance access and declared return shape. */
  StatementCompiler(
      CompilationContext context,
      boolean instanceAccessAllowed,
      SubroutineCatalog catalog,
      String returnType) {
    this.context = Objects.requireNonNull(context, "context");
    Objects.requireNonNull(returnType, "returnType");
    if (returnType.isBlank()) {
      throw new IllegalArgumentException("Jack return type must not be blank.");
    }
    this.instanceAccessAllowed = instanceAccessAllowed;
    this.returnsVoid = returnType.equals("void");
    this.expressions = new ExpressionCompiler(context, instanceAccessAllowed, catalog);
  }

  /** Compiles statements in source order. Conditional and loop statements follow in P3-13. */
  void compile(GrammarNode statements) {
    Objects.requireNonNull(statements, "statements");
    if (statements.tag() != SyntaxTag.STATEMENTS) {
      throw CompilationException.atNode("Expected Jack statements.", statements);
    }

    for (ParseNode child : statements.children()) {
      if (!(child instanceof GrammarNode statement)) {
        throw CompilationException.atNode("Expected a Jack statement.", child);
      }
      switch (statement.tag()) {
        case LET_STATEMENT -> compileLet(statement);
        case DO_STATEMENT -> compileDo(statement);
        case RETURN_STATEMENT -> compileReturn(statement);
        case IF_STATEMENT, WHILE_STATEMENT ->
            throw CompilationException.atNode(
                "Jack conditional and loop statements are not yet supported.", statement);
        default -> throw CompilationException.atNode("Unsupported Jack statement.", statement);
      }
    }
  }

  private void compileLet(GrammarNode statement) {
    List<ParseNode> children = statement.children();
    boolean indexed = children.size() == 8;
    if (!indexed && children.size() != 5) {
      throw CompilationException.atNode("Malformed Jack let statement.", statement);
    }

    requireKeyword(children.get(0), "let");
    Token targetName = requireToken(children.get(1), TokenType.IDENTIFIER);
    GrammarNode index = null;
    GrammarNode value;
    if (indexed) {
      requireSymbol(children.get(2), "[");
      index = requireGrammar(children.get(3), SyntaxTag.EXPRESSION);
      requireSymbol(children.get(4), "]");
      requireSymbol(children.get(5), "=");
      value = requireGrammar(children.get(6), SyntaxTag.EXPRESSION);
      requireSymbol(children.get(7), ";");
    } else {
      requireSymbol(children.get(2), "=");
      value = requireGrammar(children.get(3), SyntaxTag.EXPRESSION);
      requireSymbol(children.get(4), ";");
    }

    Symbol target = resolveTarget(targetName);
    VmSegment segment = segmentOf(target);
    if (!indexed) {
      expressions.compile(value);
      context.writer().writePop(segment, target.index());
      return;
    }

    // An indexed assignment needs the address and its value on the stack.
    // Preserve the value while restoring THAT after any nested array reads.
    context.writer().writePush(segment, target.index());
    expressions.compile(index);
    context.writer().writeArithmetic(VmArithmetic.ADD);
    expressions.compile(value);
    context.writer().writePop(VmSegment.TEMP, 0);
    context.writer().writePop(VmSegment.POINTER, 1);
    context.writer().writePush(VmSegment.TEMP, 0);
    context.writer().writePop(VmSegment.THAT, 0);
  }

  private void compileDo(GrammarNode statement) {
    List<ParseNode> children = statement.children();
    if (children.size() != 6 && children.size() != 8) {
      throw CompilationException.atNode("Malformed Jack do statement.", statement);
    }
    requireKeyword(children.get(0), "do");
    requireSymbol(children.get(children.size() - 1), ";");
    expressions.compileCall(children.subList(1, children.size() - 1));
    context.writer().writePop(VmSegment.TEMP, 0);
  }

  private void compileReturn(GrammarNode statement) {
    List<ParseNode> children = statement.children();
    if (children.size() != 2 && children.size() != 3) {
      throw CompilationException.atNode("Malformed Jack return statement.", statement);
    }
    requireKeyword(children.get(0), "return");
    requireSymbol(children.get(children.size() - 1), ";");

    boolean hasValue = children.size() == 3;
    if (hasValue == returnsVoid) {
      throw CompilationException.atNode(
          returnsVoid
              ? "A void Jack subroutine cannot return a value."
              : "A non-void Jack subroutine must return a value.",
          hasValue ? children.get(1) : statement);
    }

    if (hasValue) {
      expressions.compile(requireGrammar(children.get(1), SyntaxTag.EXPRESSION));
    } else {
      context.writer().writePush(VmSegment.CONSTANT, 0);
    }
    context.writer().writeReturn();
  }

  private Symbol resolveTarget(Token name) {
    Symbol target =
        context
            .symbols()
            .resolve(name.value())
            .orElseThrow(
                () ->
                    new CompilationException(
                        "Undefined Jack variable '" + name.value() + "'.", name.position()));
    if (target.kind() == SymbolKind.FIELD && !instanceAccessAllowed) {
      throw new CompilationException(
          "Cannot access field '" + name.value() + "' inside a Jack function.", name.position());
    }
    return target;
  }

  private static VmSegment segmentOf(Symbol symbol) {
    return switch (symbol.kind()) {
      case STATIC -> VmSegment.STATIC;
      case FIELD -> VmSegment.THIS;
      case ARGUMENT -> VmSegment.ARGUMENT;
      case LOCAL -> VmSegment.LOCAL;
    };
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
