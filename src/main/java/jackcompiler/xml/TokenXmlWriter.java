package jackcompiler.xml;

import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.token.TokenizationResult;
import java.util.Objects;

/** Serializes Jack lexical tokens into the nand2tetris token XML format. */
public final class TokenXmlWriter {

  /** Produces a complete token XML document without an XML declaration. */
  public String toXml(TokenizationResult result) {
    Objects.requireNonNull(result, "result");

    StringBuilder xml = new StringBuilder("<tokens>\n");

    for (Token token : result.tokens()) {
      xml.append(formatToken(token)).append('\n');
    }

    return xml.append("</tokens>\n").toString();
  }

  /** Formats one terminal without indentation or line breaks for reuse by syntax XML writers. */
  static String formatToken(Token token) {
    Objects.requireNonNull(token, "token");

    String tag = tagFor(token.type());
    return "<" + tag + "> " + XmlEscaper.escape(token.value()) + " </" + tag + ">";
  }

  private static String tagFor(TokenType type) {
    return switch (type) {
      case KEYWORD -> "keyword";
      case SYMBOL -> "symbol";
      case INTEGER_CONSTANT -> "integerConstant";
      case STRING_CONSTANT -> "stringConstant";
      case IDENTIFIER -> "identifier";
    };
  }
}
