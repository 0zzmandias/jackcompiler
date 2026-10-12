package jackcompiler.token;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of lexical analysis.
 *
 * <p>The end position represents EOF independently of the token list. No artificial EOF token is
 * created.
 *
 * @param tokens lexical tokens in source order
 * @param endPosition position immediately after the source
 */
public record TokenizationResult(List<Token> tokens, SourcePosition endPosition) {

  public TokenizationResult {
    tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
    Objects.requireNonNull(endPosition, "endPosition");
  }
}
