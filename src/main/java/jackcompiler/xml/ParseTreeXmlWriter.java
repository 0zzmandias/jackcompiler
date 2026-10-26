package jackcompiler.xml;

import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import java.util.Objects;

/** Serializes immutable Jack concrete syntax trees into the official nand2tetris XML format. */
public final class ParseTreeXmlWriter {

  /**
   * Produces syntax XML with two-space indentation, LF line endings and no XML declaration.
   *
   * @param root root grammar node to serialize
   * @return the complete XML document, including its final line feed
   * @throws NullPointerException if {@code root} is null
   * @throws IllegalArgumentException if a terminal contains characters forbidden by XML 1.0
   */
  public String toXml(GrammarNode root) {
    Objects.requireNonNull(root, "root");

    StringBuilder xml = new StringBuilder();
    appendNode(root, xml, 0);
    return xml.toString();
  }

  private static void appendNode(ParseNode node, StringBuilder xml, int depth) {
    switch (node) {
      case GrammarNode grammar -> {
        String tag = tagFor(grammar.tag());

        appendIndent(xml, depth);
        xml.append('<').append(tag).append(">\n");

        for (ParseNode child : grammar.children()) {
          appendNode(child, xml, depth + 1);
        }

        appendIndent(xml, depth);
        xml.append("</").append(tag).append(">\n");
      }
      case TerminalNode terminal -> {
        appendIndent(xml, depth);
        xml.append(TokenXmlWriter.formatToken(terminal.token())).append('\n');
      }
    }
  }

  private static void appendIndent(StringBuilder xml, int depth) {
    xml.append("  ".repeat(depth));
  }

  private static String tagFor(SyntaxTag tag) {
    return switch (tag) {
      case CLASS -> "class";
      case CLASS_VAR_DEC -> "classVarDec";
      case SUBROUTINE_DEC -> "subroutineDec";
      case PARAMETER_LIST -> "parameterList";
      case SUBROUTINE_BODY -> "subroutineBody";
      case VAR_DEC -> "varDec";
      case STATEMENTS -> "statements";
      case LET_STATEMENT -> "letStatement";
      case IF_STATEMENT -> "ifStatement";
      case WHILE_STATEMENT -> "whileStatement";
      case DO_STATEMENT -> "doStatement";
      case RETURN_STATEMENT -> "returnStatement";
      case EXPRESSION -> "expression";
      case TERM -> "term";
      case EXPRESSION_LIST -> "expressionList";
    };
  }
}
