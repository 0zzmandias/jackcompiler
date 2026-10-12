package jackcompiler.token;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TokenTest {

  @Test
  void definesAllFiveJackTokenCategories() {
    assertArrayEquals(
        new TokenType[] {
          TokenType.KEYWORD,
          TokenType.SYMBOL,
          TokenType.INTEGER_CONSTANT,
          TokenType.STRING_CONSTANT,
          TokenType.IDENTIFIER
        },
        TokenType.values());
  }

  @Test
  void preservesTypeValueAndPosition() {
    SourcePosition position = new SourcePosition(2, 5);
    Token token = new Token(TokenType.IDENTIFIER, "counter", position);

    assertEquals(TokenType.IDENTIFIER, token.type());
    assertEquals("counter", token.value());
    assertEquals(position, token.position());
  }

  @Test
  void permitsEmptyStringContent() {
    Token token = new Token(TokenType.STRING_CONSTANT, "", new SourcePosition(1, 1));

    assertEquals("", token.value());
  }

  @Test
  void rejectsNullComponents() {
    SourcePosition position = new SourcePosition(1, 1);

    assertThrows(NullPointerException.class, () -> new Token(null, "x", position));
    assertThrows(NullPointerException.class, () -> new Token(TokenType.IDENTIFIER, null, position));
    assertThrows(NullPointerException.class, () -> new Token(TokenType.IDENTIFIER, "x", null));
  }
}
