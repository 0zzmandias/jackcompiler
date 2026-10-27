package jackcompiler.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.lexer.LexicalException;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.token.TokenizationResult;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Verifies the public lexer contract and the approved internal dependency boundaries. */
class LexerPublicApiTest {

  private static final Path SOURCE_ROOT = Path.of("src/main/java/jackcompiler");

  private static final Map<String, Set<String>> ALLOWED_DEPENDENCIES =
      Map.of(
          "token", Set.of(),
          "lexer", Set.of("token"),
          "xml", Set.of("token", "syntax"),
          "io", Set.of(),
          "application", Set.of("lexer", "token", "xml", "io", "parser"),
          "cli", Set.of("application", "lexer", "token", "parser"));

  private static final Pattern PACKAGE_DECLARATION =
      Pattern.compile("(?m)^\\s*package\\s+jackcompiler\\.([a-z]+)\\s*;");

  // Covers imports and fully qualified references to any internal package.
  private static final Pattern INTERNAL_REFERENCE =
      Pattern.compile("\\bjackcompiler\\.([A-Za-z_][A-Za-z0-9_]*)\\.");

  @Test
  void publicTokenizerCanBeUsedFromOutsideTheLexerPackage() {
    JackTokenizer tokenizer = new JackTokenizer();

    TokenizationResult result = tokenizer.tokenize("class Main { let a[0] = 32767; }");

    assertEquals(
        List.of("class", "Main", "{", "let", "a", "[", "0", "]", "=", "32767", ";", "}"),
        result.tokens().stream().map(Token::value).toList());
    assertEquals(TokenType.KEYWORD, result.tokens().get(0).type());
    assertEquals(TokenType.INTEGER_CONSTANT, result.tokens().get(6).type());
    assertEquals(new SourcePosition(1, 1), result.tokens().get(0).position());
    assertEquals(new SourcePosition(1, 33), result.endPosition());

    assertEquals(
        List.of("return", ";"),
        tokenizer.tokenize("return;").tokens().stream().map(Token::value).toList());
  }

  @Test
  void publicTypesAndMethodSignaturesRemainAvailable() throws NoSuchMethodException {
    assertTrue(Modifier.isPublic(JackTokenizer.class.getModifiers()));
    assertTrue(Modifier.isPublic(LexicalException.class.getModifiers()));
    assertTrue(Modifier.isPublic(Token.class.getModifiers()));
    assertTrue(Modifier.isPublic(TokenType.class.getModifiers()));
    assertTrue(Modifier.isPublic(SourcePosition.class.getModifiers()));
    assertTrue(Modifier.isPublic(TokenizationResult.class.getModifiers()));

    assertTrue(Modifier.isPublic(JackTokenizer.class.getConstructor().getModifiers()));
    assertEquals(
        TokenizationResult.class,
        JackTokenizer.class.getMethod("tokenize", String.class).getReturnType());
    assertEquals(
        SourcePosition.class, LexicalException.class.getMethod("position").getReturnType());

    assertTrue(Token.class.isRecord());
    assertTrue(SourcePosition.class.isRecord());
    assertTrue(TokenizationResult.class.isRecord());
    assertTrue(TokenType.class.isEnum());
  }

  @Test
  void lexicalErrorsAreExposedThroughThePublicContract() {
    LexicalException error =
        assertThrows(LexicalException.class, () -> new JackTokenizer().tokenize("class @"));

    assertEquals(new SourcePosition(1, 7), error.position());
    assertTrue(error.getMessage().contains("U+0040"));
  }

  @Test
  void lexerImplementationTypesRemainPackagePrivate() throws ClassNotFoundException {
    for (String name :
        List.of(
            "SourceCursor",
            "JackLexicalRules",
            "TokenRecognizer",
            "IdentifierRecognizer",
            "IntegerRecognizer",
            "StringRecognizer",
            "SymbolRecognizer",
            "TriviaConsumer",
            "WhitespaceConsumer",
            "LineCommentConsumer",
            "BlockCommentConsumer",
            "TriviaSkipper")) {
      Class<?> type = Class.forName("jackcompiler.lexer." + name);
      assertFalse(Modifier.isPublic(type.getModifiers()), name + " must not be public");
    }
  }

  @Test
  void productionPackagesRespectApprovedDependencyDirections() throws IOException {
    assertTrue(Files.isDirectory(SOURCE_ROOT), "Production source root not found: " + SOURCE_ROOT);

    for (var entry : ALLOWED_DEPENDENCIES.entrySet()) {
      String owningPackage = entry.getKey();
      Set<String> allowed = entry.getValue();
      Path directory = SOURCE_ROOT.resolve(owningPackage);
      assertTrue(Files.isDirectory(directory), "Missing production package: " + directory);

      List<Path> sources;
      try (Stream<Path> stream = Files.walk(directory)) {
        sources = stream.filter(path -> path.toString().endsWith(".java")).sorted().toList();
      }
      assertFalse(sources.isEmpty(), "No Java sources found in " + directory);

      for (Path sourceFile : sources) {
        String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
        Matcher declaration = PACKAGE_DECLARATION.matcher(source);
        assertTrue(declaration.find(), "Missing package declaration in " + sourceFile);
        assertEquals(owningPackage, declaration.group(1), "Unexpected package in " + sourceFile);

        Matcher references = INTERNAL_REFERENCE.matcher(source);
        while (references.find()) {
          String referencedPackage = references.group(1);
          if (!owningPackage.equals(referencedPackage)) {
            assertTrue(
                allowed.contains(referencedPackage),
                sourceFile
                    + " contains a forbidden dependency on jackcompiler."
                    + referencedPackage);
          }
        }
      }
    }
  }
}
