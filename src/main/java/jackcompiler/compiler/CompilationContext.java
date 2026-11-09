package jackcompiler.compiler;

import jackcompiler.symbol.SymbolTable;
import jackcompiler.vm.VmWriter;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Holds independent symbol, emission, and label state for one Jack class compilation. */
final class CompilationContext {

  private static final Pattern JACK_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  private final String className;
  private final SymbolTable symbols = new SymbolTable();
  private final VmWriter writer = new VmWriter();

  private String subroutineName;
  private boolean method;
  private long nextLabelIndex;

  CompilationContext(String className) {
    this.className = requireIdentifier(className, "class name");
  }

  /** Returns the class being compiled. */
  String className() {
    return className;
  }

  /** Returns the symbol table owned by this compilation. */
  SymbolTable symbols() {
    return symbols;
  }

  /** Returns the writer shared by all subroutines of the current class. */
  VmWriter writer() {
    return writer;
  }

  /** Returns the current subroutine name, if one has been started. */
  Optional<String> currentSubroutineName() {
    return Optional.ofNullable(subroutineName);
  }

  /** Returns the current subroutine's VM name in Class.subroutine form. */
  String qualifiedSubroutineName() {
    return className + "." + requireSubroutine();
  }

  /** Reports whether the current subroutine is a method. */
  boolean isMethod() {
    requireSubroutine();
    return method;
  }

  /** Starts an independent subroutine scope and resets its label numbering. */
  void startSubroutine(String name, boolean isMethod) {
    String validatedName = requireIdentifier(name, "subroutine name");
    symbols.startSubroutine(isMethod);
    subroutineName = validatedName;
    method = isMethod;
    nextLabelIndex = 0;
  }

  /** Creates a deterministic label unique within the current subroutine. */
  String nextLabel(String prefix) {
    requireSubroutine();
    String validatedPrefix = requireIdentifier(prefix, "label prefix");
    return validatedPrefix + "$" + nextLabelIndex++;
  }

  private String requireSubroutine() {
    if (subroutineName == null) {
      throw new IllegalStateException("No Jack subroutine has been started.");
    }
    return subroutineName;
  }

  private static String requireIdentifier(String value, String description) {
    Objects.requireNonNull(value, description);
    if (!JACK_IDENTIFIER.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid Jack " + description + ": " + value);
    }
    return value;
  }
}
