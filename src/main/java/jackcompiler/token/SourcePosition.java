package jackcompiler.token;

/**
 * One-based position in a Jack source file.
 *
 * @param line source line, starting at 1
 * @param column source column, starting at 1
 */
public record SourcePosition(int line, int column) {

  public SourcePosition {
    if (line < 1) {
      throw new IllegalArgumentException("Line must be positive.");
    }
    if (column < 1) {
      throw new IllegalArgumentException("Column must be positive.");
    }
  }
}
