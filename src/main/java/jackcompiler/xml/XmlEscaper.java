package jackcompiler.xml;

import java.util.Objects;

/** Escapes token content as valid XML 1.0 character data for XML serializers. */
final class XmlEscaper {

  private XmlEscaper() {}

  /** Escapes XML-reserved characters and rejects characters forbidden by XML 1.0. */
  static String escape(String value) {
    Objects.requireNonNull(value, "value");

    StringBuilder escaped = new StringBuilder(value.length());

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
