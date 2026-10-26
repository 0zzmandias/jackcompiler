package jackcompiler.parser;

import static jackcompiler.parser.ClassStructureParserTestSupport.assertGrammar;
import static jackcompiler.parser.ClassStructureParserTestSupport.assertTerminal;
import static jackcompiler.parser.ClassStructureParserTestSupport.fixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.parser.ClassStructureParserTestSupport.Fixture;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.SyntaxTag;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassStructureParserDeclarationTest {

  @Test
  void parsesStaticVariableDeclarationWithOriginalTerminals() {
    Fixture f = fixture("static int count;");

    GrammarNode declaration = f.parser().parseClassVarDec();

    assertGrammar(declaration, SyntaxTag.CLASS_VAR_DEC, 4);
    for (int i = 0; i < 4; i++) {
      assertTerminal(declaration.children().get(i), f.result().tokens().get(i));
    }
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesFieldDeclarationWithClassNameAsType() {
    Fixture f = fixture("field Point origin;");

    GrammarNode declaration = f.parser().parseClassVarDec();

    assertGrammar(declaration, SyntaxTag.CLASS_VAR_DEC, 4);
    assertTerminal(declaration.children().get(0), f.result().tokens().get(0));
    assertTerminal(declaration.children().get(1), f.result().tokens().get(1));
    assertEquals("Point", f.result().tokens().get(1).value());
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void acceptsAllThreePrimitiveTypesForClassVariables() {
    for (String type : List.of("int", "char", "boolean")) {
      Fixture f = fixture("field " + type + " value;");

      GrammarNode declaration = f.parser().parseClassVarDec();

      assertGrammar(declaration, SyntaxTag.CLASS_VAR_DEC, 4);
      assertTerminal(declaration.children().get(1), f.result().tokens().get(1));
      assertTrue(f.cursor().isAtEnd());
    }
  }

  @Test
  void preservesCommasAndVariableNamesInClassDeclarations() {
    Fixture f = fixture("field int x, y, z;");

    GrammarNode declaration = f.parser().parseClassVarDec();

    assertGrammar(declaration, SyntaxTag.CLASS_VAR_DEC, 8);
    for (int i = 0; i < 8; i++) {
      assertTerminal(declaration.children().get(i), f.result().tokens().get(i));
    }
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void permitsRepeatedVariableNamesWithoutSemanticValidation() {
    Fixture f = fixture("field int x, x;");

    GrammarNode declaration = f.parser().parseClassVarDec();

    assertGrammar(declaration, SyntaxTag.CLASS_VAR_DEC, 6);
    assertEquals("x", f.result().tokens().get(2).value());
    assertEquals("x", f.result().tokens().get(4).value());
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void rejectsVoidAsClassVariableType() {
    Fixture f = fixture("static void count;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseClassVarDec());

    assertEquals("type ('int', 'char', 'boolean' or class name)", error.expected());
    assertSame(f.result().tokens().get(1), error.found().orElseThrow());
    assertSame(f.result().tokens().get(1), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsKeywordConstantAsVariableType() {
    Fixture f = fixture("field true value;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseClassVarDec());

    assertEquals("type ('int', 'char', 'boolean' or class name)", error.expected());
    assertSame(f.result().tokens().get(1), error.found().orElseThrow());
    assertSame(f.result().tokens().get(1), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresClassVariableNameAfterType() {
    Fixture f = fixture("field int =;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseClassVarDec());

    assertEquals("token of type IDENTIFIER", error.expected());
    assertSame(f.result().tokens().get(2), error.found().orElseThrow());
    assertSame(f.result().tokens().get(2), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresCommaBetweenClassVariableNames() {
    Fixture f = fixture("field int x y;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseClassVarDec());

    assertEquals("SYMBOL ';'", error.expected());
    assertSame(f.result().tokens().get(3), error.found().orElseThrow());
    assertSame(f.result().tokens().get(3), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsTrailingCommaInClassVariableDeclaration() {
    Fixture f = fixture("field int x,;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseClassVarDec());

    assertEquals("token of type IDENTIFIER", error.expected());
    assertSame(f.result().tokens().get(4), error.found().orElseThrow());
    assertSame(f.result().tokens().get(4), f.cursor().peek().orElseThrow());
  }

  @Test
  void reportsEofWhenClassVariableSemicolonIsMissing() {
    Fixture f = fixture("static int count");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseClassVarDec());

    assertEquals("SYMBOL ';'", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(f.result().endPosition(), error.position());
  }

  @Test
  void rejectsInvalidClassVariableDeclarationKeyword() {
    Fixture f = fixture("var int count;");

    SyntaxException error =
        assertThrows(SyntaxException.class, () -> f.parser().parseClassVarDec());

    assertEquals("class variable declaration ('static' or 'field')", error.expected());
    assertSame(f.result().tokens().get(0), error.found().orElseThrow());
    assertSame(f.result().tokens().get(0), f.cursor().peek().orElseThrow());
  }

  @Test
  void parsesLocalVariableDeclarationWithOriginalTerminals() {
    Fixture f = fixture("var int index;");

    GrammarNode declaration = f.parser().parseVarDec();

    assertGrammar(declaration, SyntaxTag.VAR_DEC, 4);
    for (int i = 0; i < 4; i++) {
      assertTerminal(declaration.children().get(i), f.result().tokens().get(i));
    }
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void parsesMultipleLocalVariablesWithClassType() {
    Fixture f = fixture("var Point first, second;");

    GrammarNode declaration = f.parser().parseVarDec();

    assertGrammar(declaration, SyntaxTag.VAR_DEC, 6);
    for (int i = 0; i < 6; i++) {
      assertTerminal(declaration.children().get(i), f.result().tokens().get(i));
    }
    assertTrue(f.cursor().isAtEnd());
  }

  @Test
  void acceptsAllThreePrimitiveTypesForLocalVariables() {
    for (String type : List.of("int", "char", "boolean")) {
      Fixture f = fixture("var " + type + " value;");

      GrammarNode declaration = f.parser().parseVarDec();

      assertGrammar(declaration, SyntaxTag.VAR_DEC, 4);
      assertTerminal(declaration.children().get(1), f.result().tokens().get(1));
      assertTrue(f.cursor().isAtEnd());
    }
  }

  @Test
  void rejectsVoidAsLocalVariableType() {
    Fixture f = fixture("var void value;");

    SyntaxException error = assertThrows(SyntaxException.class, () -> f.parser().parseVarDec());

    assertEquals("type ('int', 'char', 'boolean' or class name)", error.expected());
    assertSame(f.result().tokens().get(1), error.found().orElseThrow());
    assertSame(f.result().tokens().get(1), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresLocalVariableType() {
    Fixture f = fixture("var ;");

    SyntaxException error = assertThrows(SyntaxException.class, () -> f.parser().parseVarDec());

    assertEquals("type ('int', 'char', 'boolean' or class name)", error.expected());
    assertSame(f.result().tokens().get(1), error.found().orElseThrow());
    assertSame(f.result().tokens().get(1), f.cursor().peek().orElseThrow());
  }

  @Test
  void requiresLocalVariableName() {
    Fixture f = fixture("var boolean ;");

    SyntaxException error = assertThrows(SyntaxException.class, () -> f.parser().parseVarDec());

    assertEquals("token of type IDENTIFIER", error.expected());
    assertSame(f.result().tokens().get(2), error.found().orElseThrow());
    assertSame(f.result().tokens().get(2), f.cursor().peek().orElseThrow());
  }

  @Test
  void rejectsConsecutiveCommasInLocalVariableDeclaration() {
    Fixture f = fixture("var int a,, b;");

    SyntaxException error = assertThrows(SyntaxException.class, () -> f.parser().parseVarDec());

    assertEquals("token of type IDENTIFIER", error.expected());
    assertSame(f.result().tokens().get(4), error.found().orElseThrow());
    assertSame(f.result().tokens().get(4), f.cursor().peek().orElseThrow());
  }

  @Test
  void reportsMissingLocalVariableSemicolonAtEof() {
    Fixture f = fixture("var int value");

    SyntaxException error = assertThrows(SyntaxException.class, () -> f.parser().parseVarDec());

    assertEquals("SYMBOL ';'", error.expected());
    assertTrue(error.found().isEmpty());
    assertEquals(f.result().endPosition(), error.position());
  }

  @Test
  void rejectsWrongKeywordAtBeginningOfLocalVariableDeclaration() {
    Fixture f = fixture("field int value;");

    SyntaxException error = assertThrows(SyntaxException.class, () -> f.parser().parseVarDec());

    assertEquals("KEYWORD 'var'", error.expected());
    assertSame(f.result().tokens().get(0), error.found().orElseThrow());
    assertSame(f.result().tokens().get(0), f.cursor().peek().orElseThrow());
  }

  @Test
  void leavesFollowingStatementUnconsumedAfterLocalDeclaration() {
    Fixture f = fixture("var int value; return;");

    GrammarNode declaration = f.parser().parseVarDec();

    assertGrammar(declaration, SyntaxTag.VAR_DEC, 4);
    assertSame(f.result().tokens().get(4), f.cursor().peek().orElseThrow());
    assertEquals("return", f.cursor().peek().orElseThrow().value());
  }
}
