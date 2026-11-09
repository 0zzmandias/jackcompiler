package jackcompiler.compiler;

import jackcompiler.syntax.GrammarNode;
import java.util.Objects;

/** Public entry point for translating one parsed Jack class into VM instructions. */
public final class JackVmCompiler {

  /** Creates a compiler facade with no retained compilation state. */
  public JackVmCompiler() {}

  /**
   * Compiles a complete Jack class concrete syntax tree into Hack VM source text.
   *
   * <p>Each invocation has independent symbol tables, subroutine scopes, labels and VM output. The
   * result uses LF line endings and ends with LF when nonempty. This method performs no source
   * reading, tokenization, parsing or file output.
   *
   * @param classTree parsed Jack class, rooted at {@code SyntaxTag.CLASS}
   * @return generated VM program as a string, or the empty string for a class without routines
   * @throws NullPointerException if {@code classTree} is null
   * @throws CompilationException if the class tree or its semantics are invalid
   */
  public String compile(GrammarNode classTree) {
    return new ClassCompiler().compile(Objects.requireNonNull(classTree, "classTree"));
  }
}
