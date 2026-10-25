package jackcompiler.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import org.junit.jupiter.api.Test;

class TerminalNodeTest {

  @Test
  void retainsTheExactTokenWithOriginalTypeValueAndPosition() {
    Token token = new Token(TokenType.IDENTIFIER, "name", new SourcePosition(3, 7));

    TerminalNode node = new TerminalNode(token);

    assertSame(token, node.token());
    assertEquals(TokenType.IDENTIFIER, node.token().type());
    assertEquals("name", node.token().value());
    assertEquals(new SourcePosition(3, 7), node.token().position());
  }

  @Test
  void preservesEmptyAndWhitespaceSignificantStringContents() {
    TerminalNode empty =
        new TerminalNode(new Token(TokenType.STRING_CONSTANT, "", new SourcePosition(1, 1)));
    TerminalNode spaced =
        new TerminalNode(new Token(TokenType.STRING_CONSTANT, " a ", new SourcePosition(2, 1)));

    assertEquals("", empty.token().value());
    assertEquals(" a ", spaced.token().value());
  }

  @Test
  void rejectsNullToken() {
    assertThrows(NullPointerException.class, () -> new TerminalNode(null));
  }
}
