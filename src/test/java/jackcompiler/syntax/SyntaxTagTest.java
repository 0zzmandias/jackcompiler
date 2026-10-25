package jackcompiler.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class SyntaxTagTest {

  @Test
  void definesExactlyTheFifteenOfficialNonterminalCategories() {
    assertEquals(15, SyntaxTag.values().length);
    assertEquals(
        EnumSet.of(
            SyntaxTag.CLASS,
            SyntaxTag.CLASS_VAR_DEC,
            SyntaxTag.SUBROUTINE_DEC,
            SyntaxTag.PARAMETER_LIST,
            SyntaxTag.SUBROUTINE_BODY,
            SyntaxTag.VAR_DEC,
            SyntaxTag.STATEMENTS,
            SyntaxTag.LET_STATEMENT,
            SyntaxTag.IF_STATEMENT,
            SyntaxTag.WHILE_STATEMENT,
            SyntaxTag.DO_STATEMENT,
            SyntaxTag.RETURN_STATEMENT,
            SyntaxTag.EXPRESSION,
            SyntaxTag.TERM,
            SyntaxTag.EXPRESSION_LIST),
        EnumSet.allOf(SyntaxTag.class));
  }
}
