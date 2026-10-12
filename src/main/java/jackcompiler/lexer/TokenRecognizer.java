package jackcompiler.lexer;

import jackcompiler.token.Token;

/** Contract for recognizing one lexical token at the current source position. */
interface TokenRecognizer {

  /** Checks whether this recognizer supports the next token without consuming input. */
  boolean canRecognize(SourceCursor cursor);

  /** Reads one token and advances the cursor past its contents. */
  Token recognize(SourceCursor cursor);
}
