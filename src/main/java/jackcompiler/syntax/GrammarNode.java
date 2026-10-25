package jackcompiler.syntax;

import java.util.List;
import java.util.Objects;

/**
 * Immutable concrete syntax node for a Jack grammar production.
 *
 * @param tag grammatical category represented by this node
 * @param children terminal and grammar nodes in source order
 */
public record GrammarNode(SyntaxTag tag, List<ParseNode> children) implements ParseNode {

  public GrammarNode {
    Objects.requireNonNull(tag, "tag");
    children = List.copyOf(Objects.requireNonNull(children, "children"));
  }
}
