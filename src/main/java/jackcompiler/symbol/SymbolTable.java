package jackcompiler.symbol;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Symbol definitions for one Jack class and its current subroutine. */
public final class SymbolTable {

  private final Map<String, Symbol> classSymbols = new HashMap<>();
  private final Map<String, Symbol> subroutineSymbols = new HashMap<>();
  private final EnumMap<SymbolKind, Integer> counts = new EnumMap<>(SymbolKind.class);

  public SymbolTable() {
    for (SymbolKind kind : SymbolKind.values()) {
      counts.put(kind, 0);
    }
  }

  /**
   * Starts a new subroutine scope while preserving class-level symbols.
   *
   * @param isMethod whether argument index zero is reserved for the implicit receiver
   */
  public void startSubroutine(boolean isMethod) {
    subroutineSymbols.clear();
    counts.put(SymbolKind.ARGUMENT, isMethod ? 1 : 0);
    counts.put(SymbolKind.LOCAL, 0);
  }

  /**
   * Defines a symbol in the scope associated with its kind.
   *
   * @param name declared variable name
   * @param type declared Jack type
   * @param kind declaration category
   * @return the newly defined symbol
   * @throws IllegalArgumentException if the name already exists in the same scope
   */
  public Symbol define(String name, String type, SymbolKind kind) {
    Objects.requireNonNull(kind, "kind");

    int index = counts.get(kind);
    Symbol symbol = new Symbol(name, type, kind, index);

    Map<String, Symbol> scope =
        switch (kind) {
          case STATIC, FIELD -> classSymbols;
          case ARGUMENT, LOCAL -> subroutineSymbols;
        };

    if (scope.containsKey(name)) {
      throw new IllegalArgumentException("Duplicate symbol in the same scope: " + name);
    }

    scope.put(name, symbol);
    counts.put(kind, index + 1);
    return symbol;
  }

  /** Returns the number of slots assigned to a symbol kind. */
  public int count(SymbolKind kind) {
    return counts.get(Objects.requireNonNull(kind, "kind"));
  }

  /** Resolves a name, preferring the subroutine scope over the class scope. */
  public Optional<Symbol> resolve(String name) {
    Objects.requireNonNull(name, "name");

    Symbol symbol = subroutineSymbols.get(name);
    if (symbol == null) {
      symbol = classSymbols.get(name);
    }

    return Optional.ofNullable(symbol);
  }
}
