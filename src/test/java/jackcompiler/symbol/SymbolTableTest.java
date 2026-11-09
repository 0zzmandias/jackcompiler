package jackcompiler.symbol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SymbolTableTest {

  @Test
  void startsWithZeroCountForEveryKind() {
    SymbolTable table = new SymbolTable();

    for (SymbolKind kind : SymbolKind.values()) {
      assertEquals(0, table.count(kind));
    }
  }

  @Test
  void assignsIndependentClassIndices() {
    SymbolTable table = new SymbolTable();

    assertEquals(0, table.define("first", "int", SymbolKind.STATIC).index());
    assertEquals(0, table.define("width", "int", SymbolKind.FIELD).index());
    assertEquals(1, table.define("second", "boolean", SymbolKind.STATIC).index());
    assertEquals(1, table.define("height", "int", SymbolKind.FIELD).index());

    assertEquals(2, table.count(SymbolKind.STATIC));
    assertEquals(2, table.count(SymbolKind.FIELD));
  }

  @Test
  void assignsIndependentSubroutineIndices() {
    SymbolTable table = new SymbolTable();
    table.startSubroutine(false);

    assertEquals(0, table.define("x", "int", SymbolKind.ARGUMENT).index());
    assertEquals(0, table.define("result", "int", SymbolKind.LOCAL).index());
    assertEquals(1, table.define("y", "int", SymbolKind.ARGUMENT).index());
    assertEquals(1, table.define("temporary", "int", SymbolKind.LOCAL).index());

    assertEquals(2, table.count(SymbolKind.ARGUMENT));
    assertEquals(2, table.count(SymbolKind.LOCAL));
  }

  @Test
  void reservesFirstArgumentIndexForMethods() {
    SymbolTable table = new SymbolTable();
    table.startSubroutine(true);

    assertEquals(1, table.count(SymbolKind.ARGUMENT));
    assertEquals(0, table.count(SymbolKind.LOCAL));

    Symbol parameter = table.define("distance", "int", SymbolKind.ARGUMENT);

    assertEquals(1, parameter.index());
    assertEquals(2, table.count(SymbolKind.ARGUMENT));
    assertEquals(Optional.empty(), table.resolve("this"));
  }

  @Test
  void startsFunctionsAndConstructorsWithoutImplicitReceiver() {
    SymbolTable table = new SymbolTable();

    table.startSubroutine(false);
    assertEquals(0, table.define("value", "int", SymbolKind.ARGUMENT).index());

    table.startSubroutine(false);
    assertEquals(0, table.define("size", "int", SymbolKind.ARGUMENT).index());
  }

  @Test
  void resetsArgumentOffsetsBetweenMethodsAndFunctions() {
    SymbolTable table = new SymbolTable();

    table.startSubroutine(true);
    assertEquals(1, table.define("first", "int", SymbolKind.ARGUMENT).index());

    table.startSubroutine(false);
    assertEquals(0, table.define("second", "int", SymbolKind.ARGUMENT).index());

    table.startSubroutine(true);
    assertEquals(1, table.define("third", "int", SymbolKind.ARGUMENT).index());

    assertEquals(Optional.empty(), table.resolve("first"));
    assertEquals(Optional.empty(), table.resolve("second"));
  }

  @Test
  void resetsSubroutineScopeAndPreservesClassScope() {
    SymbolTable table = new SymbolTable();

    Symbol field = table.define("width", "int", SymbolKind.FIELD);
    Symbol staticSymbol = table.define("total", "int", SymbolKind.STATIC);

    table.startSubroutine(false);
    table.define("parameter", "int", SymbolKind.ARGUMENT);
    table.define("temporary", "int", SymbolKind.LOCAL);

    table.startSubroutine(false);

    assertEquals(Optional.empty(), table.resolve("parameter"));
    assertEquals(Optional.empty(), table.resolve("temporary"));
    assertEquals(Optional.of(field), table.resolve("width"));
    assertEquals(Optional.of(staticSymbol), table.resolve("total"));

    assertEquals(0, table.count(SymbolKind.ARGUMENT));
    assertEquals(0, table.count(SymbolKind.LOCAL));
    assertEquals(1, table.count(SymbolKind.STATIC));
    assertEquals(1, table.count(SymbolKind.FIELD));
  }

  @Test
  void resolvesSubroutineSymbolsBeforeClassSymbols() {
    SymbolTable table = new SymbolTable();

    Symbol field = table.define("value", "int", SymbolKind.FIELD);

    table.startSubroutine(false);
    Symbol local = table.define("value", "boolean", SymbolKind.LOCAL);

    assertEquals(Optional.of(local), table.resolve("value"));

    table.startSubroutine(false);

    assertEquals(Optional.of(field), table.resolve("value"));
  }

  @Test
  void rejectsDuplicateClassDeclarationsAcrossKinds() {
    SymbolTable table = new SymbolTable();
    Symbol original = table.define("value", "int", SymbolKind.STATIC);

    assertThrows(
        IllegalArgumentException.class, () -> table.define("value", "boolean", SymbolKind.FIELD));

    assertEquals(Optional.of(original), table.resolve("value"));
    assertEquals(1, table.count(SymbolKind.STATIC));
    assertEquals(0, table.count(SymbolKind.FIELD));
  }

  @Test
  void rejectsDuplicateSubroutineDeclarationsAcrossKinds() {
    SymbolTable table = new SymbolTable();
    table.startSubroutine(false);

    Symbol original = table.define("value", "int", SymbolKind.ARGUMENT);

    assertThrows(
        IllegalArgumentException.class, () -> table.define("value", "boolean", SymbolKind.LOCAL));

    assertEquals(Optional.of(original), table.resolve("value"));
    assertEquals(1, table.count(SymbolKind.ARGUMENT));
    assertEquals(0, table.count(SymbolKind.LOCAL));
  }

  @Test
  void allowsSameNameInDifferentScopes() {
    SymbolTable table = new SymbolTable();

    table.define("counter", "int", SymbolKind.STATIC);
    table.startSubroutine(false);
    Symbol argument = table.define("counter", "boolean", SymbolKind.ARGUMENT);

    assertEquals(Optional.of(argument), table.resolve("counter"));
    assertEquals(1, table.count(SymbolKind.STATIC));
    assertEquals(1, table.count(SymbolKind.ARGUMENT));
  }

  @Test
  void returnsEmptyForUnknownIdentifiers() {
    SymbolTable table = new SymbolTable();

    assertEquals(Optional.empty(), table.resolve("missing"));

    table.define("known", "int", SymbolKind.FIELD);

    assertEquals(Optional.empty(), table.resolve("missing"));
  }

  @Test
  void keepsDifferentClassTablesIndependent() {
    SymbolTable first = new SymbolTable();
    SymbolTable second = new SymbolTable();

    first.define("counter", "int", SymbolKind.FIELD);

    assertEquals(Optional.empty(), second.resolve("counter"));
    assertEquals(0, second.count(SymbolKind.FIELD));

    Symbol separate = second.define("counter", "boolean", SymbolKind.FIELD);

    assertEquals(0, separate.index());
    assertEquals("boolean", separate.type());
    assertEquals(1, first.count(SymbolKind.FIELD));
  }

  @Test
  void rejectsNullDefinitionComponentsWithoutChangingCounts() {
    SymbolTable table = new SymbolTable();

    assertThrows(NullPointerException.class, () -> table.define(null, "int", SymbolKind.LOCAL));
    assertThrows(NullPointerException.class, () -> table.define("value", null, SymbolKind.LOCAL));
    assertThrows(NullPointerException.class, () -> table.define("value", "int", null));

    assertEquals(0, table.count(SymbolKind.LOCAL));
  }

  @Test
  void rejectsBlankNamesAndTypesWithoutChangingCounts() {
    SymbolTable table = new SymbolTable();

    assertThrows(IllegalArgumentException.class, () -> table.define("", "int", SymbolKind.FIELD));
    assertThrows(IllegalArgumentException.class, () -> table.define("  ", "int", SymbolKind.FIELD));
    assertThrows(IllegalArgumentException.class, () -> table.define("value", "", SymbolKind.FIELD));
    assertThrows(
        IllegalArgumentException.class, () -> table.define("value", "  ", SymbolKind.FIELD));

    assertEquals(0, table.count(SymbolKind.FIELD));
  }

  @Test
  void rejectsNullLookupAndCountKind() {
    SymbolTable table = new SymbolTable();

    assertThrows(NullPointerException.class, () -> table.resolve(null));
    assertThrows(NullPointerException.class, () -> table.count(null));
  }
}
