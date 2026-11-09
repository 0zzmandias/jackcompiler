package jackcompiler.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.compiler.CompilationException;
import jackcompiler.compiler.JackVmCompiler;
import jackcompiler.lexer.JackTokenizer;
import jackcompiler.parser.JackParser;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.token.SourcePosition;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guards the public Jack-to-VM contract and the compiler's dependency boundary. */
class CompilerPublicApiTest {

  private static final Path COMPILER_SOURCE_ROOT = Path.of("src/main/java/jackcompiler/compiler");
  private static final Pattern INTERNAL_IMPORT =
      Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?jackcompiler\\.([a-z]+)\\.");
  private static final Set<String> ALLOWED_COMPILER_DEPENDENCIES =
      Set.of("compiler", "token", "syntax", "symbol", "vm");

  @Test
  void facadeIsAccessibleFromOutsideCompilerPackage() {
    GrammarNode tree =
        new JackParser()
            .parse(
                new JackTokenizer().tokenize("class Main { function int answer() { return 8; } }"));
    String vm = new JackVmCompiler().compile(tree);
    assertEquals("function Main.answer 0\npush constant 8\nreturn\n", vm);
  }

  @Test
  void facadeAndCompilationErrorArePublic() {
    assertTrue(Modifier.isPublic(JackVmCompiler.class.getModifiers()));
    assertTrue(Modifier.isFinal(JackVmCompiler.class.getModifiers()));
    assertTrue(Modifier.isPublic(CompilationException.class.getModifiers()));
  }

  @Test
  void publicConstructorAndCompileMethodHaveStableSignature() throws NoSuchMethodException {
    assertTrue(Modifier.isPublic(JackVmCompiler.class.getConstructor().getModifiers()));
    var method = JackVmCompiler.class.getMethod("compile", GrammarNode.class);
    assertTrue(Modifier.isPublic(method.getModifiers()));
    assertEquals(String.class, method.getReturnType());
    assertEquals(List.of(GrammarNode.class), List.of(method.getParameterTypes()));
  }

  @Test
  void facadeExposesOnlyTheSingleCompilationOperation() {
    var declared = JackVmCompiler.class.getDeclaredMethods();
    assertEquals(1, declared.length);
    assertEquals("compile", declared[0].getName());
    assertFalse(Modifier.isStatic(declared[0].getModifiers()));
  }

  @Test
  void publicFacadeHasNoFieldsOrSharedMutableState() {
    assertEquals(0, JackVmCompiler.class.getDeclaredFields().length);
  }

  @Test
  void facadeDoesNotExposeStringOrTokenizationInputs() {
    assertThrows(
        NoSuchMethodException.class, () -> JackVmCompiler.class.getMethod("compile", String.class));
    assertThrows(
        NoSuchMethodException.class,
        () -> JackVmCompiler.class.getMethod("tokenize", String.class));
    assertThrows(
        NoSuchMethodException.class, () -> JackVmCompiler.class.getMethod("parse", String.class));
  }

  @Test
  void internalCompilerTypesRemainPackagePrivate() throws ClassNotFoundException {
    for (String type :
        List.of(
            "ClassCompiler",
            "StatementCompiler",
            "ExpressionCompiler",
            "CompilationContext",
            "SubroutineCatalog",
            "SyntaxTreeNavigator")) {
      assertFalse(
          Modifier.isPublic(Class.forName("jackcompiler.compiler." + type).getModifiers()),
          type + " must not become public");
    }
  }

  @Test
  void publicErrorCarriesSourcePositionAndDescription() {
    CompilationException error =
        new CompilationException("Undefined Jack variable 'value'.", new SourcePosition(4, 7));
    assertEquals("Undefined Jack variable 'value'.", error.description());
    assertEquals(new SourcePosition(4, 7), error.position().orElseThrow());
    assertTrue(error.getMessage().contains("line 4, column 7"));
  }

  @Test
  void publicFacadeReportsInvalidSyntaxTreeWithoutHidingCompilationError() {
    CompilationException error =
        assertThrows(
            CompilationException.class,
            () -> new JackVmCompiler().compile(new GrammarNode(SyntaxTag.STATEMENTS, List.of())));
    assertTrue(error.description().contains("Expected a Jack class declaration"));
  }

  @Test
  void compilerProductionSourcesDoNotImportHigherLayers() throws IOException {
    assertTrue(Files.isDirectory(COMPILER_SOURCE_ROOT));
    try (Stream<Path> sources = Files.walk(COMPILER_SOURCE_ROOT)) {
      for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
        String content = Files.readString(source, StandardCharsets.UTF_8);
        Matcher imports = INTERNAL_IMPORT.matcher(content);
        while (imports.find()) {
          assertTrue(
              ALLOWED_COMPILER_DEPENDENCIES.contains(imports.group(1)),
              source + " imports forbidden jackcompiler." + imports.group(1));
        }
      }
    }
  }
}
