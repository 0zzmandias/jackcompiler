package jackcompiler.compiler;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.parser.JackParser;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import java.util.List;

/** Shared fixtures for expression-compilation tests, without production logic. */
final class ExpressionCompilerTestSupport {

  private ExpressionCompilerTestSupport() {}

  static String compile(String sourceExpression) {
    CompilationContext context = new CompilationContext("Main");
    new ExpressionCompiler(context).compile(parseExpression(sourceExpression));
    return context.writer().toVm();
  }

  static SubroutineCatalog catalog(String classSource) {
    return SubroutineCatalog.fromClass(
        new JackParser().parse(new JackTokenizer().tokenize(classSource)));
  }

  static GrammarNode parseExpression(String sourceExpression) {
    return parseExpressionWithHeader(sourceExpression, "function void run()");
  }

  static GrammarNode parseExpressionWithHeader(String sourceExpression, String subroutineHeader) {
    String source = "class Main { " + subroutineHeader + " { return " + sourceExpression + "; } }";
    GrammarNode root = new JackParser().parse(new JackTokenizer().tokenize(source));
    GrammarNode subroutine =
        SyntaxTreeNavigator.grammarChildren(root, SyntaxTag.SUBROUTINE_DEC).get(0);
    GrammarNode body = SyntaxTreeNavigator.grammarAt(subroutine, 6, SyntaxTag.SUBROUTINE_BODY);
    GrammarNode statements = SyntaxTreeNavigator.grammarChildren(body, SyntaxTag.STATEMENTS).get(0);
    GrammarNode statement =
        SyntaxTreeNavigator.grammarChildren(statements, SyntaxTag.RETURN_STATEMENT).get(0);
    return SyntaxTreeNavigator.grammarAt(statement, 1, SyntaxTag.EXPRESSION);
  }

  static GrammarNode expression(ParseNode... children) {
    return new GrammarNode(SyntaxTag.EXPRESSION, List.of(children));
  }

  static GrammarNode term(ParseNode... children) {
    return new GrammarNode(SyntaxTag.TERM, List.of(children));
  }

  static TerminalNode integer(String value, int column) {
    return terminal(TokenType.INTEGER_CONSTANT, value, column);
  }

  static TerminalNode symbol(String value, int column) {
    return terminal(TokenType.SYMBOL, value, column);
  }

  static TerminalNode terminal(TokenType type, String value, int column) {
    return new TerminalNode(new Token(type, value, new SourcePosition(3, column)));
  }
}
