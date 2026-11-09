package jackcompiler.symbol;

import java.util.Objects;

/**
 * Immutable binding of a Jack variable declaration to its kind and index.
 *
 * @param name declared variable name
 * @param type declared Jack type
 * @param kind declaration category and index space
 * @param index zero-based index within its category
 */
public record Symbol(String name, String type, SymbolKind kind, int index) {

  public Symbol {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(kind, "kind");

    if (name.isBlank()) {
      throw new IllegalArgumentException("Symbol name must not be blank.");
    }
    if (type.isBlank()) {
      throw new IllegalArgumentException("Symbol type must not be blank.");
    }
    if (index < 0) {
      throw new IllegalArgumentException("Symbol index must be non-negative.");
    }
  }
}
