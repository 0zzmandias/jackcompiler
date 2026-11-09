package jackcompiler.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.symbol.Symbol;
import jackcompiler.symbol.SymbolKind;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompilationContextTest {

  @Test
  void retainsClassNameAndOwnedState() {
    CompilationContext context = new CompilationContext("SquareGame");

    assertEquals("SquareGame", context.className());
    assertSame(context.symbols(), context.symbols());
    assertSame(context.writer(), context.writer());
    assertEquals(0, context.symbols().count(SymbolKind.FIELD));
    assertEquals("", context.writer().toVm());
  }

  @Test
  void startsWithoutAnActiveSubroutine() {
    CompilationContext context = new CompilationContext("Main");

    assertEquals(Optional.empty(), context.currentSubroutineName());
    assertThrows(IllegalStateException.class, context::qualifiedSubroutineName);
    assertThrows(IllegalStateException.class, context::isMethod);
    assertThrows(IllegalStateException.class, () -> context.nextLabel("IF_TRUE"));
  }

  @Test
  void beginsFunctionWithFullyQualifiedNameAndZeroArgumentOffset() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);

    assertEquals(Optional.of("run"), context.currentSubroutineName());
    assertEquals("Main.run", context.qualifiedSubroutineName());
    assertFalse(context.isMethod());
    assertEquals(0, context.symbols().count(SymbolKind.ARGUMENT));
    assertEquals(0, context.symbols().define("count", "int", SymbolKind.ARGUMENT).index());
  }

  @Test
  void reservesImplicitReceiverForMethod() {
    CompilationContext context = new CompilationContext("Point");
    context.startSubroutine("move", true);

    assertTrue(context.isMethod());
    assertEquals(1, context.symbols().count(SymbolKind.ARGUMENT));
    assertEquals(1, context.symbols().define("dx", "int", SymbolKind.ARGUMENT).index());
    assertEquals(0, context.symbols().define("temporary", "int", SymbolKind.LOCAL).index());
  }

  @Test
  void beginsConstructorWithoutImplicitArgument() {
    CompilationContext context = new CompilationContext("Point");
    context.startSubroutine("new", false);

    assertFalse(context.isMethod());
    assertEquals("Point.new", context.qualifiedSubroutineName());
    assertEquals(0, context.symbols().define("x", "int", SymbolKind.ARGUMENT).index());
  }

  @Test
  void retainsClassSymbolsBetweenSubroutines() {
    CompilationContext context = new CompilationContext("Main");
    Symbol counter = context.symbols().define("counter", "int", SymbolKind.STATIC);
    Symbol field = context.symbols().define("position", "Point", SymbolKind.FIELD);

    context.startSubroutine("first", false);
    context.symbols().define("local", "int", SymbolKind.LOCAL);
    context.startSubroutine("second", true);

    assertEquals(Optional.of(counter), context.symbols().resolve("counter"));
    assertEquals(Optional.of(field), context.symbols().resolve("position"));
    assertEquals(1, context.symbols().count(SymbolKind.STATIC));
    assertEquals(1, context.symbols().count(SymbolKind.FIELD));
    assertEquals(Optional.empty(), context.symbols().resolve("local"));
  }

  @Test
  void resetsLocalDeclarationsAndCounters() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("first", false);
    context.symbols().define("a", "int", SymbolKind.ARGUMENT);
    context.symbols().define("b", "int", SymbolKind.LOCAL);

    context.startSubroutine("second", false);

    assertEquals(0, context.symbols().count(SymbolKind.ARGUMENT));
    assertEquals(0, context.symbols().count(SymbolKind.LOCAL));
    assertEquals(Optional.empty(), context.symbols().resolve("a"));
    assertEquals(Optional.empty(), context.symbols().resolve("b"));
    assertEquals(0, context.symbols().define("c", "int", SymbolKind.LOCAL).index());
  }

  @Test
  void transitionsBetweenMethodAndFunctionArgumentOffsets() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("methodOne", true);
    assertEquals(1, context.symbols().define("first", "int", SymbolKind.ARGUMENT).index());

    context.startSubroutine("functionOne", false);
    assertEquals(0, context.symbols().define("second", "int", SymbolKind.ARGUMENT).index());

    context.startSubroutine("methodTwo", true);
    assertEquals(1, context.symbols().define("third", "int", SymbolKind.ARGUMENT).index());
  }

  @Test
  void generatesUniqueLabelsAcrossPrefixesAndNestedBlocks() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);

    assertEquals("IF_TRUE$0", context.nextLabel("IF_TRUE"));
    assertEquals("WHILE_EXP$1", context.nextLabel("WHILE_EXP"));
    assertEquals("IF_TRUE$2", context.nextLabel("IF_TRUE"));
    assertEquals("WHILE_END$3", context.nextLabel("WHILE_END"));
  }

  @Test
  void restartsLabelNumbersAtEachSubroutineBoundary() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("first", false);
    assertEquals("IF_END$0", context.nextLabel("IF_END"));
    assertEquals("IF_END$1", context.nextLabel("IF_END"));

    context.startSubroutine("second", false);
    assertEquals("IF_END$0", context.nextLabel("IF_END"));
  }

  @Test
  void generatedLabelsAreAcceptedByVmWriter() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);

    context.writer().writeLabel(context.nextLabel("WHILE_EXP"));
    context.writer().writeLabel(context.nextLabel("WHILE_END"));

    assertEquals("label WHILE_EXP$0\nlabel WHILE_END$1\n", context.writer().toVm());
  }

  @Test
  void preservesVmOutputAcrossSubroutineChanges() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("first", false);
    context.writer().writeLabel(context.nextLabel("START"));
    context.startSubroutine("second", false);
    context.writer().writeLabel(context.nextLabel("START"));

    assertEquals("label START$0\nlabel START$0\n", context.writer().toVm());
  }

  @Test
  void isolatesSymbolsWriterAndLabelsAcrossClassCompilations() {
    CompilationContext first = new CompilationContext("Main");
    CompilationContext second = new CompilationContext("Main");
    assertNotSame(first.symbols(), second.symbols());
    assertNotSame(first.writer(), second.writer());

    first.symbols().define("counter", "int", SymbolKind.STATIC);
    first.startSubroutine("run", false);
    second.startSubroutine("run", false);
    first.writer().writeLabel(first.nextLabel("LOOP"));

    assertEquals(Optional.empty(), second.symbols().resolve("counter"));
    assertEquals("LOOP$0", second.nextLabel("LOOP"));
    assertEquals("", second.writer().toVm());
  }

  @Test
  void rejectsNullBlankAndMalformedClassNames() {
    assertThrows(NullPointerException.class, () -> new CompilationContext(null));

    for (String invalid :
        new String[] {"", " ", "1Main", "Main.name", "Main-name", "Main\nClass"}) {
      assertThrows(IllegalArgumentException.class, () -> new CompilationContext(invalid));
    }
  }

  @Test
  void rejectsInvalidSubroutineNamesWithoutResettingExistingState() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("valid", true);
    Symbol local = context.symbols().define("value", "int", SymbolKind.LOCAL);
    assertEquals("LOOP$0", context.nextLabel("LOOP"));

    assertThrows(NullPointerException.class, () -> context.startSubroutine(null, false));
    for (String invalid : new String[] {"", " ", "2run", "bad.name", "bad-name"}) {
      assertThrows(IllegalArgumentException.class, () -> context.startSubroutine(invalid, false));
    }

    assertEquals("Main.valid", context.qualifiedSubroutineName());
    assertTrue(context.isMethod());
    assertEquals(Optional.of(local), context.symbols().resolve("value"));
    assertEquals("LOOP$1", context.nextLabel("LOOP"));
  }

  @Test
  void rejectsInvalidLabelPrefixesWithoutConsumingIndices() {
    CompilationContext context = new CompilationContext("Main");
    context.startSubroutine("run", false);
    assertEquals("LOOP$0", context.nextLabel("LOOP"));

    assertThrows(NullPointerException.class, () -> context.nextLabel(null));
    for (String invalid : new String[] {"", " ", "0LOOP", "LOOP$1", "LOOP.END", "bad\nlabel"}) {
      assertThrows(IllegalArgumentException.class, () -> context.nextLabel(invalid));
    }

    assertEquals("LOOP$1", context.nextLabel("LOOP"));
  }
}
