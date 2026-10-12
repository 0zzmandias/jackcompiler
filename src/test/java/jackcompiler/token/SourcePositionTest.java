package jackcompiler.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SourcePositionTest {

  @Test
  void acceptsOneBasedCoordinates() {
    SourcePosition position = new SourcePosition(3, 12);

    assertEquals(3, position.line());
    assertEquals(12, position.column());
  }

  @Test
  void rejectsNonPositiveLine() {
    assertThrows(IllegalArgumentException.class, () -> new SourcePosition(0, 1));
    assertThrows(IllegalArgumentException.class, () -> new SourcePosition(-1, 1));
  }

  @Test
  void rejectsNonPositiveColumn() {
    assertThrows(IllegalArgumentException.class, () -> new SourcePosition(1, 0));
    assertThrows(IllegalArgumentException.class, () -> new SourcePosition(1, -1));
  }
}
