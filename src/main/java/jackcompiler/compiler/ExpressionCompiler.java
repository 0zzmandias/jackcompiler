package jackcompiler.compiler;

import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.vm.VmArithmetic;
import jackcompiler.vm.VmSegment;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Compiles basic Jack expression CST nodes into VM stack instructions. */
final class ExpressionCompiler {

  private static final Set<String> BINARY_OPERATORS =
      Set.of("+", "-", "*", "/", "&", "|", "<", ">", "=");

  private final CompilationContext context;

  ExpressionCompiler(CompilationContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  /**
   * Compiles an expression in Jack's left-to-right grammatical order.
   *
   * <p>This increment supports integer constants, binary and unary operators, and explicit
   * grouping. Other term forms are introduced in later increments.
   */
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

    throw CompilationException.atNode(
        "Unsupported or malformed Jack term in basic expression compilation.", term);
  }

  private void compileAtomicTerm(ParseNode node) {
    if (!(node instanceof TerminalNode terminal)) {
      throw CompilationException.atNode("Expected an atomic Jack term.", node);
    }

    Token token = terminal.token();
    if (token.type() != TokenType.INTEGER_CONSTANT) {
      throw CompilationException.atNode(
          "Jack term '" + token.value() + "' is not yet supported.", node);
    }

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

  private static Token requireSymbol(ParseNode node) {
    try {
      return SyntaxTreeNavigator.requireToken(node, TokenType.SYMBOL);
    } catch (IllegalArgumentException cause) {
      throw CompilationException.atNode("Expected a Jack symbol.", node, cause);
    }
  }

  private static void requireSymbol(ParseNode node, String value) {
    Token token = requireSymbol(node);
    if (!token.value().equals(value)) {
      throw CompilationException.atNode("Expected Jack symbol '" + value + "'.", node);
    }
  }
}
