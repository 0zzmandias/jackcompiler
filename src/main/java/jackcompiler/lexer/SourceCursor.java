package jackcompiler.lexer;

import jackcompiler.token.SourcePosition;
import java.util.Objects;

/** Reads Jack source text using logical Unicode code points and normalized line endings. */
final class SourceCursor {

  static final int EOF = -1;

  private final String source;
  private int index;
  private int line = 1;
  private int column = 1;

  SourceCursor(String source) {
    this.source = Objects.requireNonNull(source, "source");
  }

  boolean isAtEnd() {
    return index >= source.length();
  }

  SourcePosition position() {
    return new SourcePosition(line, column);
  }

  int peek() {
    return peek(0);
  }

  /** Reads a logical character at a nonnegative offset without advancing. */
  int peek(int offset) {
    if (offset < 0) {
      throw new IllegalArgumentException("Lookahead offset cannot be negative.");
    }

    int lookaheadIndex = index;

    for (int i = 0; i < offset && lookaheadIndex < source.length(); i++) {
      lookaheadIndex += widthAt(source, lookaheadIndex);
    }

    return lookaheadIndex < source.length() ? logicalCodePointAt(source, lookaheadIndex) : EOF;
  }

  /** Consumes one logical character, normalizing CR, LF and CRLF to LF. */
  int advance() {
    if (isAtEnd()) {
      throw new IllegalStateException("Cannot advance beyond the end of source.");
    }

    int codePoint = logicalCodePointAt(source, index);
    index += widthAt(source, index);

    if (codePoint == '\n') {
      line++;
      column = 1;
    } else {
      column++;
    }

    return codePoint;
  }

  /** Checks a logical prefix without advancing the cursor. */
  boolean startsWith(String prefix) {
    Objects.requireNonNull(prefix, "prefix");

    int currentIndex = index;
    int prefixIndex = 0;

    while (prefixIndex < prefix.length()) {
      if (currentIndex >= source.length()
          || logicalCodePointAt(source, currentIndex) != logicalCodePointAt(prefix, prefixIndex)) {
        return false;
      }

      currentIndex += widthAt(source, currentIndex);
      prefixIndex += widthAt(prefix, prefixIndex);
    }

    return true;
  }

  private static int logicalCodePointAt(String text, int index) {
    int codePoint = text.codePointAt(index);
    return codePoint == '\r' ? '\n' : codePoint;
  }

  private static int widthAt(String text, int index) {
    if (text.charAt(index) == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
      return 2;
    }

    return Character.charCount(text.codePointAt(index));
  }
}
