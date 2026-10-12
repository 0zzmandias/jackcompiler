package jackcompiler.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenizationResultTest {

  @Test
  void preservesTokenOrderAndEndPosition() {
    Token first = new Token(TokenType.KEYWORD, "class", new SourcePosition(1, 1));
    Token second = new Token(TokenType.IDENTIFIER, "Main", new SourcePosition(1, 7));
    SourcePosition endPosition = new SourcePosition(1, 11);

    TokenizationResult result = new TokenizationResult(List.of(first, second), endPosition);

    assertEquals(List.of(first, second), result.tokens());
    assertEquals(endPosition, result.endPosition());
  }

  @Test
  void acceptsEmptyTokenList() {
    TokenizationResult result = new TokenizationResult(List.of(), new SourcePosition(1, 1));

    assertEquals(List.of(), result.tokens());
    assertEquals(new SourcePosition(1, 1), result.endPosition());
  }

  @Test
  void protectsAgainstChangesToOriginalList() {
    Token token = new Token(TokenType.SYMBOL, "{", new SourcePosition(1, 1));
    List<Token> original = new ArrayList<>();
    original.add(token);

    TokenizationResult result = new TokenizationResult(original, new SourcePosition(1, 2));

    original.clear();

    assertEquals(List.of(token), result.tokens());
  }

  @Test
  void exposesUnmodifiableTokenList() {
    TokenizationResult result = new TokenizationResult(List.of(), new SourcePosition(1, 1));

    Token token = new Token(TokenType.SYMBOL, "}", new SourcePosition(1, 1));

    assertThrows(UnsupportedOperationException.class, () -> result.tokens().add(token));
  }

  @Test
  void rejectsNullArgumentsAndNullTokens() {
    SourcePosition endPosition = new SourcePosition(1, 1);

    assertThrows(NullPointerException.class, () -> new TokenizationResult(null, endPosition));
    assertThrows(NullPointerException.class, () -> new TokenizationResult(List.of(), null));
    assertThrows(
        NullPointerException.class,
        () -> new TokenizationResult(Arrays.asList((Token) null), endPosition));
  }
}
