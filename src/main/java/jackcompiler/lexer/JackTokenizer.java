package jackcompiler.lexer;

import jackcompiler.token.Token;
import jackcompiler.token.TokenizationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Coordinates the lexical analysis of Jack source code. */
public final class JackTokenizer {

  private static final List<TokenRecognizer> RECOGNIZERS =
      List.of(
          new StringRecognizer(),
          new IntegerRecognizer(),
          new IdentifierRecognizer(),
          new SymbolRecognizer());

  private final TriviaSkipper triviaSkipper = new TriviaSkipper();

  /**
   * Converts Jack source code into an immutable sequence of tokens.
   *
   * @param source Jack source text
   * @return tokens in source order and the final source position
   * @throws LexicalException if the source contains an invalid lexical element
   */
  public TokenizationResult tokenize(String source) {
    SourceCursor cursor = new SourceCursor(Objects.requireNonNull(source, "source"));
    List<Token> tokens = new ArrayList<>();

    while (true) {
      triviaSkipper.skip(cursor);

      if (cursor.isAtEnd()) {
        return new TokenizationResult(tokens, cursor.position());
      }

      tokens.add(recognizeNextToken(cursor));
    }
  }

  private Token recognizeNextToken(SourceCursor cursor) {
    for (TokenRecognizer recognizer : RECOGNIZERS) {
      if (recognizer.canRecognize(cursor)) {
        return recognizer.recognize(cursor);
      }
    }

    throw new LexicalException(
        String.format("Unexpected character U+%04X.", cursor.peek()), cursor.position());
  }
}
