package jackcompiler.syntax;

/** Grammatical categories represented as nonterminal nodes in the Jack syntax tree. */
public enum SyntaxTag {
  CLASS,
  CLASS_VAR_DEC,
  SUBROUTINE_DEC,
  PARAMETER_LIST,
  SUBROUTINE_BODY,
  VAR_DEC,
  STATEMENTS,
  LET_STATEMENT,
  IF_STATEMENT,
  WHILE_STATEMENT,
  DO_STATEMENT,
  RETURN_STATEMENT,
  EXPRESSION,
  TERM,
  EXPRESSION_LIST
}
