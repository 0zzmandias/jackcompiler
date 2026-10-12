package jackcompiler.lexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.token.SourcePosition;
import org.junit.jupiter.api.Test;

class LexicalExceptionTest {

  @Test
  void preservesMessageAndSourcePosition() {
    SourcePosition position = new SourcePosition(4, 8);

    LexicalException exception = new LexicalException("Invalid character.", position);

    assertEquals("Invalid character.", exception.getMessage());
    assertEquals(position, exception.position());
  }

  @Test
  void isAnUncheckedException() {
    LexicalException exception = new LexicalException("Invalid token.", new SourcePosition(1, 1));

    assertTrue(exception instanceof RuntimeException);
  }

  @Test
  void rejectsNullMessage() {
    SourcePosition position = new SourcePosition(1, 1);

    assertThrows(NullPointerException.class, () -> new LexicalException(null, position));
  }

  @Test
  void rejectsNullPosition() {
    assertThrows(NullPointerException.class, () -> new LexicalException("Invalid token.", null));
  }
}
