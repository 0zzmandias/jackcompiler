package jackcompiler.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompilationExceptionTest {

  @Test
  void isAnUncheckedExceptionDistinctFromParsingErrors() {
    CompilationException error = new CompilationException("Unknown identifier 'x'.");

    assertTrue(error instanceof RuntimeException);
    assertEquals("Unknown identifier 'x'.", error.description());
  }

  @Test
  void reportsUnpositionedDiagnosticWithoutInventingCoordinates() {
    CompilationException error = new CompilationException("Missing class definition.");

    assertEquals(Optional.empty(), error.position());
    assertEquals("Compilation error: Missing class definition.", error.getMessage());
  }

  @Test
  void reportsOriginalSourcePositionAndReadableMessage() {
    SourcePosition position = new SourcePosition(8, 13);
    CompilationException error = new CompilationException("Unknown variable 'x'.", position);

    assertSame(position, error.position().orElseThrow());
    assertEquals("Unknown variable 'x'.", error.description());
    assertEquals(
        "Compilation error at line 8, column 13: Unknown variable 'x'.", error.getMessage());
  }

  @Test
  void usesOriginalTokenPositionForTerminalSubtree() {
    Token token = new Token(TokenType.IDENTIFIER, "missing", new SourcePosition(3, 9));
    CompilationException error =
        CompilationException.atNode("Undefined identifier 'missing'.", new TerminalNode(token));

    assertSame(token.position(), error.position().orElseThrow());
    assertEquals(
        "Compilation error at line 3, column 9: Undefined identifier 'missing'.",
        error.getMessage());
  }

  @Test
  void findsFirstAvailableTokenInsideNestedSubtree() {
    Token first = new Token(TokenType.KEYWORD, "return", new SourcePosition(5, 4));
    Token last = new Token(TokenType.SYMBOL, ";", new SourcePosition(5, 10));
    GrammarNode tree =
        new GrammarNode(
            SyntaxTag.EXPRESSION,
            List.of(
                new GrammarNode(SyntaxTag.TERM, List.of()),
                new GrammarNode(SyntaxTag.TERM, List.of(new TerminalNode(first))),
                new TerminalNode(last)));

    CompilationException error = CompilationException.atNode("Invalid expression.", tree);

    assertSame(first.position(), error.position().orElseThrow());
  }

  @Test
  void doesNotFabricatePositionForEmptySubtree() {
    GrammarNode empty =
        new GrammarNode(SyntaxTag.EXPRESSION, List.of(new GrammarNode(SyntaxTag.TERM, List.of())));
    CompilationException error = CompilationException.atNode("Malformed tree.", empty);

    assertEquals(Optional.empty(), error.position());
    assertEquals("Compilation error: Malformed tree.", error.getMessage());
  }

  @Test
  void preservesCauseForPositionedError() {
    IllegalArgumentException cause = new IllegalArgumentException("duplicate declaration");
    SourcePosition position = new SourcePosition(2, 7);
    CompilationException error = new CompilationException("Duplicate variable.", position, cause);

    assertSame(cause, error.getCause());
    assertSame(position, error.position().orElseThrow());
  }

  @Test
  void preservesCauseAndPositionWhenCreatedFromSubtree() {
    IllegalArgumentException cause = new IllegalArgumentException("invalid binding");
    Token token = new Token(TokenType.IDENTIFIER, "x", new SourcePosition(6, 12));
    CompilationException error =
        CompilationException.atNode("Invalid binding.", new TerminalNode(token), cause);

    assertSame(cause, error.getCause());
    assertSame(token.position(), error.position().orElseThrow());
  }

  @Test
  void preservesCauseWithoutFabricatingPositionForEmptySubtree() {
    IllegalArgumentException cause = new IllegalArgumentException("invalid structure");
    CompilationException error =
        CompilationException.atNode(
            "Invalid structure.", new GrammarNode(SyntaxTag.TERM, List.of()), cause);

    assertSame(cause, error.getCause());
    assertEquals(Optional.empty(), error.position());
  }

  @Test
  void rejectsNullAndBlankDescriptions() {
    SourcePosition position = new SourcePosition(1, 1);
    assertThrows(NullPointerException.class, () -> new CompilationException(null));
    assertThrows(NullPointerException.class, () -> new CompilationException(null, position));
    for (String invalid : new String[] {"", " ", "\t "}) {
      assertThrows(IllegalArgumentException.class, () -> new CompilationException(invalid));
      assertThrows(
          IllegalArgumentException.class, () -> new CompilationException(invalid, position));
    }
  }

  @Test
  void rejectsNullExplicitPositionAndCause() {
    SourcePosition position = new SourcePosition(1, 1);
    assertThrows(
        NullPointerException.class,
        () -> new CompilationException("Invalid value.", (SourcePosition) null));
    assertThrows(
        NullPointerException.class,
        () -> new CompilationException("Invalid value.", position, null));
  }

  @Test
  void rejectsNullSubtreesAndNullCausesInFactories() {
    TerminalNode node =
        new TerminalNode(new Token(TokenType.IDENTIFIER, "x", new SourcePosition(1, 1)));

    assertThrows(NullPointerException.class, () -> CompilationException.atNode("Invalid.", null));
    assertThrows(
        NullPointerException.class, () -> CompilationException.atNode("Invalid.", node, null));
  }
}
