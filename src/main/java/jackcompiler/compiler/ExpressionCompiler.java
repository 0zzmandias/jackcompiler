package jackcompiler.compiler;

import jackcompiler.symbol.Symbol;
import jackcompiler.symbol.SymbolKind;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.vm.VmArithmetic;
import jackcompiler.vm.VmSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Compiles Jack expressions into VM stack instructions. */
final class ExpressionCompiler {

  private static final Set<String> BINARY_OPERATORS =
      Set.of("+", "-", "*", "/", "&", "|", ">", "<", "=");
  private static final Set<String> PRIMITIVE_TYPES = Set.of("int", "char", "boolean");

  private final CompilationContext context;
  private final boolean instanceAccessAllowed;
  private final SubroutineCatalog catalog;

  ExpressionCompiler(CompilationContext context) {
    this(context, true, null);
  }

  ExpressionCompiler(CompilationContext context, boolean instanceAccessAllowed) {
    this(context, instanceAccessAllowed, null);
  }

  /** Supplies the complete current-class catalog when subroutine calls may be compiled. */
  ExpressionCompiler(
      CompilationContext context, boolean instanceAccessAllowed, SubroutineCatalog catalog) {
    this.context = Objects.requireNonNull(context, "context");
    this.instanceAccessAllowed = instanceAccessAllowed;
    if (catalog != null && !catalog.className().equals(context.className())) {
      throw new IllegalArgumentException("Subroutine catalog belongs to a different Jack class.");
    }
    this.catalog = catalog;
  }

  /** Compiles a Jack expression from left to right, honoring explicit parentheses. */
  void compile(GrammarNode expression) {
    Objects.requireNonNull(expression, "expression");

    if (expression.tag() != SyntaxTag.EXPRESSION) {
      throw CompilationException.atNode("Expected a Jack expression.", expression);
    }

    List<ParseNode> children = expression.children();
    if (children.isEmpty() || children.size() % 2 == 0) {
      throw CompilationException.atNode("Malformed Jack expression.", expression);
    }

    compileTerm(requireGrammar(children.get(0), SyntaxTag.TERM));

    for (int index = 1; index < children.size(); index += 2) {
      ParseNode operatorNode = children.get(index);
      Token operator = requireSymbol(operatorNode);

      if (!BINARY_OPERATORS.contains(operator.value())) {
        throw CompilationException.atNode(
            "Unsupported Jack binary operator '" + operator.value() + "'.", operatorNode);
      }

      GrammarNode right = requireGrammar(children.get(index + 1), SyntaxTag.TERM);
      compileTerm(right);
      emitBinary(operator.value());
    }
  }

  /** Compiles the concrete call nodes shared by expression terms and do statements. */
  void compileCall(List<ParseNode> callChildren) {
    Objects.requireNonNull(callChildren, "callChildren");
    if (callChildren.size() != 4 && callChildren.size() != 6) {
      throw new CompilationException("Malformed Jack subroutine call.");
    }

    boolean qualified = callChildren.size() == 6;
    Token qualifier = null;
    Token name;
    int openingIndex;
    if (qualified) {
      qualifier = requireToken(callChildren.get(0), TokenType.IDENTIFIER);
      requireSymbol(callChildren.get(1), ".");
      name = requireToken(callChildren.get(2), TokenType.IDENTIFIER);
      openingIndex = 3;
    } else {
      name = requireToken(callChildren.get(0), TokenType.IDENTIFIER);
      openingIndex = 1;
    }

    requireSymbol(callChildren.get(openingIndex), "(");
    GrammarNode arguments =
        requireGrammar(callChildren.get(openingIndex + 1), SyntaxTag.EXPRESSION_LIST);
    requireSymbol(callChildren.get(openingIndex + 2), ")");
    List<GrammarNode> expressions = arguments(arguments);

    String target;
    VmSegment receiverSegment = null;
    int receiverIndex = 0;
    boolean pushThis = false;
    SubroutineCatalog.Signature signature = null;

    if (!qualified) {
      signature = lookupCurrent(name);
      target = context.className() + "." + name.value();
      if (signature.isMethod()) {
        requireInstanceAccess(new TerminalNode(name), "method '" + name.value() + "'");
        pushThis = true;
      }
    } else {
      Symbol receiver = context.symbols().resolve(qualifier.value()).orElse(null);
      if (receiver != null) {
        if (PRIMITIVE_TYPES.contains(receiver.type())) {
          throw new CompilationException(
              "Cannot call a method on primitive variable '" + qualifier.value() + "'.",
              qualifier.position());
        }
        if (receiver.kind() == SymbolKind.FIELD) {
          requireInstanceAccess(new TerminalNode(qualifier), "field '" + qualifier.value() + "'");
        }
        receiverSegment = segmentOf(receiver);
        receiverIndex = receiver.index();
        target = receiver.type() + "." + name.value();
        if (receiver.type().equals(context.className())) {
          signature = lookupCurrent(name);
          if (!signature.isMethod()) {
            throw new CompilationException(
                "Cannot call non-method subroutine '" + name.value() + "' on an object.",
                name.position());
          }
        }
      } else {
        target = qualifier.value() + "." + name.value();
        if (qualifier.value().equals(context.className())) {
          signature = lookupCurrent(name);
          if (signature.isMethod()) {
            throw new CompilationException(
                "Cannot call method '" + name.value() + "' without an object receiver.",
                name.position());
          }
        }
      }
    }

    if (signature != null && expressions.size() != signature.parameterCount()) {
      throw new CompilationException(
          "Subroutine '"
              + name.value()
              + "' expects "
              + signature.parameterCount()
              + " argument(s), found "
              + expressions.size()
              + ".",
          name.position());
    }

    if (pushThis) {
      context.writer().writePush(VmSegment.POINTER, 0);
    } else if (receiverSegment != null) {
      context.writer().writePush(receiverSegment, receiverIndex);
    }

    for (GrammarNode expression : expressions) {
      compile(expression);
    }
    int argumentCount = expressions.size() + ((pushThis || receiverSegment != null) ? 1 : 0);
    context.writer().writeCall(target, argumentCount);
  }

  private SubroutineCatalog.Signature lookupCurrent(Token name) {
    if (catalog == null) {
      throw new CompilationException(
          "Subroutine catalog is required for a call in class '" + context.className() + "'.",
          name.position());
    }
    return catalog
        .resolve(name.value())
        .orElseThrow(
            () ->
                new CompilationException(
                    "Undefined subroutine '"
                        + name.value()
                        + "' in class '"
                        + context.className()
                        + "'.",
                    name.position()));
  }

  private static List<GrammarNode> arguments(GrammarNode list) {
    List<ParseNode> children = list.children();
    if (children.isEmpty()) {
      return List.of();
    }
    if (children.size() % 2 == 0) {
      throw CompilationException.atNode("Malformed Jack argument list.", list);
    }
    List<GrammarNode> expressions = new ArrayList<>();
    for (int index = 0; index < children.size(); index++) {
      ParseNode child = children.get(index);
      if (index % 2 == 0) {
        expressions.add(requireGrammar(child, SyntaxTag.EXPRESSION));
      } else {
        requireSymbol(child, ",");
      }
    }
    return List.copyOf(expressions);
  }

  private void compileTerm(GrammarNode term) {
    List<ParseNode> children = term.children();

    if (children.size() == 1) {
      compileAtomicTerm(children.get(0));
      return;
    }
    if (children.size() == 2) {
      compileUnaryTerm(children);
      return;
    }
    if (children.size() == 3) {
      compileGroupedTerm(children);
      return;
    }
    if (children.size() == 4 || children.size() == 6) {
      if (children.size() == 4
          && children.get(1) instanceof TerminalNode separator
          && separator.token().value().equals("[")) {
        compileArrayRead(term);
        return;
      }
      compileCall(children);
      return;
    }
    throw CompilationException.atNode("Unsupported or malformed Jack term.", term);
  }

  /** Pushes the value stored at a variable base address plus a compiled index. */
  private void compileArrayRead(GrammarNode term) {
    List<ParseNode> children = term.children();
    Token arrayName = requireToken(children.get(0), TokenType.IDENTIFIER);
    requireSymbol(children.get(1), "[");
    GrammarNode index = requireGrammar(children.get(2), SyntaxTag.EXPRESSION);
    requireSymbol(children.get(3), "]");

    Symbol array =
        context
            .symbols()
            .resolve(arrayName.value())
            .orElseThrow(
                () ->
                    new CompilationException(
                        "Undefined Jack variable '" + arrayName.value() + "'.",
                        arrayName.position()));

    if (array.kind() == SymbolKind.FIELD) {
      requireInstanceAccess(new TerminalNode(arrayName), "field '" + arrayName.value() + "'");
    }

    context.writer().writePush(segmentOf(array), array.index());
    compile(index);
    context.writer().writeArithmetic(VmArithmetic.ADD);
    context.writer().writePop(VmSegment.POINTER, 1);
    context.writer().writePush(VmSegment.THAT, 0);
  }

  private void compileAtomicTerm(ParseNode node) {
    if (!(node instanceof TerminalNode terminal)) {
      throw CompilationException.atNode("Expected an atomic Jack term.", node);
    }

    Token token = terminal.token();
    switch (token.type()) {
      case INTEGER_CONSTANT -> compileInteger(node, token);
      case STRING_CONSTANT -> compileString(node, token);
      case KEYWORD -> compileKeyword(node, token);
      case IDENTIFIER -> compileVariable(node, token);
      case SYMBOL ->
          throw CompilationException.atNode(
              "Jack term '" + token.value() + "' is not yet supported.", node);
    }
  }

  private void compileInteger(ParseNode node, Token token) {
    String literal = token.value();
    if (!literal.matches("[0-9]+")) {
      throw CompilationException.atNode("Invalid Jack integer constant '" + literal + "'.", node);
    }

    final int value;
    try {
      value = Integer.parseInt(literal);
    } catch (NumberFormatException cause) {
      throw CompilationException.atNode(
          "Invalid Jack integer constant '" + literal + "'.", node, cause);
    }

    if (value > 32767) {
      throw CompilationException.atNode(
          "Jack integer constant exceeds 32767: " + literal + ".", node);
    }
    context.writer().writePush(VmSegment.CONSTANT, value);
  }

  private void compileString(ParseNode node, Token token) {
    String value = token.value();
    if (value.length() > 32767) {
      throw CompilationException.atNode(
          "Jack string constant exceeds the supported VM length of 32767.", node);
    }

    context.writer().writePush(VmSegment.CONSTANT, value.length());
    context.writer().writeCall("String.new", 1);
    for (int index = 0; index < value.length(); index++) {
      int character = value.charAt(index);
      writeCharacter(character);
      context.writer().writeCall("String.appendChar", 2);
    }
  }

  private void writeCharacter(int character) {
    if (character <= 32767) {
      context.writer().writePush(VmSegment.CONSTANT, character);
      return;
    }
    context.writer().writePush(VmSegment.CONSTANT, 65535 - character);
    context.writer().writeArithmetic(VmArithmetic.NOT);
  }

  private void compileKeyword(ParseNode node, Token token) {
    switch (token.value()) {
      case "true" -> {
        context.writer().writePush(VmSegment.CONSTANT, 0);
        context.writer().writeArithmetic(VmArithmetic.NOT);
      }
      case "false", "null" -> context.writer().writePush(VmSegment.CONSTANT, 0);
      case "this" -> {
        requireInstanceAccess(node, "this");
        context.writer().writePush(VmSegment.POINTER, 0);
      }
      default ->
          throw CompilationException.atNode(
              "Unsupported Jack keyword constant '" + token.value() + "'.", node);
    }
  }

  private void compileVariable(ParseNode node, Token token) {
    Symbol symbol =
        context
            .symbols()
            .resolve(token.value())
            .orElseThrow(
                () ->
                    CompilationException.atNode(
                        "Undefined Jack variable '" + token.value() + "'.", node));
    if (symbol.kind() == SymbolKind.FIELD) {
      requireInstanceAccess(node, "field '" + token.value() + "'");
    }
    context.writer().writePush(segmentOf(symbol), symbol.index());
  }

  private static VmSegment segmentOf(Symbol symbol) {
    return switch (symbol.kind()) {
      case STATIC -> VmSegment.STATIC;
      case FIELD -> VmSegment.THIS;
      case ARGUMENT -> VmSegment.ARGUMENT;
      case LOCAL -> VmSegment.LOCAL;
    };
  }

  private void requireInstanceAccess(ParseNode node, String subject) {
    if (!instanceAccessAllowed) {
      throw CompilationException.atNode(
          "Cannot access " + subject + " inside a Jack function.", node);
    }
  }

  private void compileUnaryTerm(List<ParseNode> children) {
    ParseNode operatorNode = children.get(0);
    Token operator = requireSymbol(operatorNode);
    if (!operator.value().equals("-") && !operator.value().equals("~")) {
      throw CompilationException.atNode(
          "Unsupported Jack unary operator '" + operator.value() + "'.", operatorNode);
    }

    GrammarNode operand = requireGrammar(children.get(1), SyntaxTag.TERM);
    compileTerm(operand);
    context
        .writer()
        .writeArithmetic(operator.value().equals("-") ? VmArithmetic.NEG : VmArithmetic.NOT);
  }

  private void compileGroupedTerm(List<ParseNode> children) {
    requireSymbol(children.get(0), "(");
    GrammarNode inner = requireGrammar(children.get(1), SyntaxTag.EXPRESSION);
    requireSymbol(children.get(2), ")");
    compile(inner);
  }

  private void emitBinary(String operator) {
    switch (operator) {
      case "+" -> context.writer().writeArithmetic(VmArithmetic.ADD);
      case "-" -> context.writer().writeArithmetic(VmArithmetic.SUB);
      case "&" -> context.writer().writeArithmetic(VmArithmetic.AND);
      case "|" -> context.writer().writeArithmetic(VmArithmetic.OR);
      case "<" -> context.writer().writeArithmetic(VmArithmetic.LT);
      case ">" -> context.writer().writeArithmetic(VmArithmetic.GT);
      case "=" -> context.writer().writeArithmetic(VmArithmetic.EQ);
      case "*" -> context.writer().writeCall("Math.multiply", 2);
      case "/" -> context.writer().writeCall("Math.divide", 2);
      default -> throw new IllegalArgumentException("Unknown binary operator: " + operator);
    }
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

  private static Token requireSymbol(ParseNode node) {
    return requireToken(node, TokenType.SYMBOL);
  }

  private static void requireSymbol(ParseNode node, String value) {
    Token token = requireSymbol(node);
    if (!token.value().equals(value)) {
      throw CompilationException.atNode("Expected Jack symbol '" + value + "'.", node);
    }
  }
}
