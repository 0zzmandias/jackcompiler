package jackcompiler.symbol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SymbolTest {

  @Test
  void definesAllFourJackSymbolKinds() {
    assertArrayEquals(
        new SymbolKind[] {
          SymbolKind.STATIC, SymbolKind.FIELD, SymbolKind.ARGUMENT, SymbolKind.LOCAL
        },
        SymbolKind.values());
  }

  @Test
  void preservesNameTypeKindAndIndex() {
    Symbol symbol = new Symbol("position", "int", SymbolKind.LOCAL, 2);

    assertEquals("position", symbol.name());
    assertEquals("int", symbol.type());
    assertEquals(SymbolKind.LOCAL, symbol.kind());
    assertEquals(2, symbol.index());
  }

  @Test
  void acceptsZeroIndexAndClassTypes() {
    Symbol symbol = new Symbol("game", "SquareGame", SymbolKind.FIELD, 0);

    assertEquals("SquareGame", symbol.type());
    assertEquals(SymbolKind.FIELD, symbol.kind());
    assertEquals(0, symbol.index());
  }

  @Test
  void usesImmutableValueSemantics() {
    Symbol first = new Symbol("count", "int", SymbolKind.STATIC, 0);
    Symbol equivalent = new Symbol("count", "int", SymbolKind.STATIC, 0);
    Symbol different = new Symbol("count", "int", SymbolKind.STATIC, 1);

    assertEquals(first, equivalent);
    assertEquals(first.hashCode(), equivalent.hashCode());
    assertNotEquals(first, different);
  }

  @Test
  void rejectsNullComponents() {
    assertThrows(NullPointerException.class, () -> new Symbol(null, "int", SymbolKind.LOCAL, 0));

    assertThrows(NullPointerException.class, () -> new Symbol("count", null, SymbolKind.LOCAL, 0));

    assertThrows(NullPointerException.class, () -> new Symbol("count", "int", null, 0));
  }

  @Test
  void rejectsBlankNamesAndTypes() {
    assertThrows(IllegalArgumentException.class, () -> new Symbol("", "int", SymbolKind.LOCAL, 0));

    assertThrows(
        IllegalArgumentException.class, () -> new Symbol(" \t", "int", SymbolKind.LOCAL, 0));

    assertThrows(
        IllegalArgumentException.class, () -> new Symbol("count", "", SymbolKind.LOCAL, 0));

    assertThrows(
        IllegalArgumentException.class, () -> new Symbol("count", "  ", SymbolKind.LOCAL, 0));
  }

  @Test
  void rejectsNegativeIndices() {
    assertThrows(
        IllegalArgumentException.class, () -> new Symbol("count", "int", SymbolKind.LOCAL, -1));

    assertThrows(
        IllegalArgumentException.class,
        () -> new Symbol("count", "int", SymbolKind.LOCAL, Integer.MIN_VALUE));
  }
}
