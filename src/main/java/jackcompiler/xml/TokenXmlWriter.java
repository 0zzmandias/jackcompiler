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
      String tag = tagFor(token.type());

      xml.append('<')
          .append(tag)
          .append("> ")
          .append(escapeXml(token.value()))
          .append(" </")
          .append(tag)
          .append(">\n");
    }

    return xml.append("</tokens>\n").toString();
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

  private static String escapeXml(String value) {
    StringBuilder escaped = new StringBuilder();

    for (int index = 0; index < value.length(); ) {
      int codePoint = value.codePointAt(index);

      if (!isValidXmlCharacter(codePoint)) {
        throw new IllegalArgumentException(
            String.format("Invalid XML character U+%04X in token value.", codePoint));
      }

      switch (codePoint) {
        case '&' -> escaped.append("&amp;");
        case '<' -> escaped.append("&lt;");
        case '>' -> escaped.append("&gt;");
        case '"' -> escaped.append("&quot;");
        case '\'' -> escaped.append("&apos;");
        default -> escaped.appendCodePoint(codePoint);
      }

      index += Character.charCount(codePoint);
    }

    return escaped.toString();
  }

  private static boolean isValidXmlCharacter(int codePoint) {
    return codePoint == 0x9
        || codePoint == 0xA
        || codePoint == 0xD
        || (codePoint >= 0x20 && codePoint <= 0xD7FF)
        || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
        || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
  }
}
