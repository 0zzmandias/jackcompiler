package jackcompiler.lexer;

import java.util.Set;

/** Centralized lexical rules of the Jack language. */
final class JackLexicalRules {

  static final int MAX_INTEGER_CONSTANT = 32767;

  private static final Set<String> KEYWORDS =
      Set.of(
          "class",
          "constructor",
          "function",
          "method",
          "field",
          "static",
          "var",
          "int",
          "char",
          "boolean",
          "void",
          "true",
          "false",
          "null",
          "this",
          "let",
          "do",
          "if",
          "else",
          "while",
          "return");

  private JackLexicalRules() {}

  static boolean isKeyword(String value) {
    return KEYWORDS.contains(value);
  }

  static boolean isSymbol(int codePoint) {
    return switch (codePoint) {
      case '{',
              '}',
              '(',
              ')',
              '[',
              ']',
              '.',
              ',',
              ';',
              '+',
              '-',
              '*',
              '/',
              '&',
              '|',
              '<',
              '>',
              '=',
              '~' ->
          true;
      default -> false;
    };
  }

  static boolean isIdentifierStart(int codePoint) {
    return codePoint == '_'
        || (codePoint >= 'A' && codePoint <= 'Z')
        || (codePoint >= 'a' && codePoint <= 'z');
  }

  static boolean isIdentifierPart(int codePoint) {
    return isIdentifierStart(codePoint) || isDecimalDigit(codePoint);
  }

  static boolean isDecimalDigit(int codePoint) {
    return codePoint >= '0' && codePoint <= '9';
  }

  static boolean isWhitespace(int codePoint) {
    return codePoint == ' ' || codePoint == '\t' || codePoint == '\n' || codePoint == '\r';
  }
}
