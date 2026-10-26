package jackcompiler.parser;

import jackcompiler.syntax.GrammarNode;
import jackcompiler.token.TokenizationResult;
import java.util.Objects;

/**
 * Public facade for recursive-descent parsing of a complete Jack class.
 *
 * <p>Receives the existing lexical result without performing tokenization, file access or XML
 * serialization. Every invocation uses independent parser state.
 */
public final class JackParser {

  /**
   * Parses exactly one Jack class from an immutable lexical result.
   *
   * @param result tokens and original end-of-input position produced by lexical analysis
   * @return the immutable concrete syntax tree rooted at {@code CLASS}
   * @throws NullPointerException if {@code result} is null
   * @throws SyntaxException if the tokens violate Jack grammar or any tokens remain after the
   *     closing class brace
   */
  public GrammarNode parse(TokenizationResult result) {
    TokenCursor cursor = new TokenCursor(Objects.requireNonNull(result, "result"));
    ExpressionParser expressions = new ExpressionParser(cursor);
    StatementParser statements = new StatementParser(cursor, expressions);
    ClassStructureParser classes = new ClassStructureParser(cursor, statements);

    GrammarNode root = classes.parseClass();

    if (!cursor.isAtEnd()) {
      throw new SyntaxException("end of input", cursor.peek(), cursor.position());
    }

    return root;
  }
}
