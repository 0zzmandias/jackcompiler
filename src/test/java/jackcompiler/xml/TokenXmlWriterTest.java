package jackcompiler.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.token.SourcePosition;
import jackcompiler.token.Token;
import jackcompiler.token.TokenType;
import jackcompiler.token.TokenizationResult;
import java.io.StringReader;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.InputSource;

class TokenXmlWriterTest {

  private final TokenXmlWriter writer = new TokenXmlWriter();

  @Test
  void writesEmptyTokenDocument() {
    TokenizationResult result = new TokenizationResult(List.of(), new SourcePosition(1, 1));

    assertEquals("<tokens>\n</tokens>\n", writer.toXml(result));
  }

  @Test
  void writesAllFiveTokenCategories() {
    SourcePosition position = new SourcePosition(1, 1);

    TokenizationResult result =
        new TokenizationResult(
            List.of(
                new Token(TokenType.KEYWORD, "class", position),
                new Token(TokenType.IDENTIFIER, "Main", position),
                new Token(TokenType.SYMBOL, "{", position),
                new Token(TokenType.INTEGER_CONSTANT, "32767", position),
                new Token(TokenType.STRING_CONSTANT, "Hello", position)),
            position);

    assertEquals(
        "<tokens>\n"
            + "<keyword> class </keyword>\n"
            + "<identifier> Main </identifier>\n"
            + "<symbol> { </symbol>\n"
            + "<integerConstant> 32767 </integerConstant>\n"
            + "<stringConstant> Hello </stringConstant>\n"
            + "</tokens>\n",
        writer.toXml(result));
  }

  @Test
  void escapesAllFiveXmlSpecialCharacters() {
    TokenizationResult result = resultWithString("&<>\"'");

    assertEquals(
        "<tokens>\n"
            + "<stringConstant> &amp;&lt;&gt;&quot;&apos; </stringConstant>\n"
            + "</tokens>\n",
        writer.toXml(result));
  }

  @Test
  void doesNotMistakeSourceEntitiesForEscapedOutput() {
    TokenizationResult result = resultWithString("&lt; &amp;");

    assertEquals(
        "<tokens>\n" + "<stringConstant> &amp;lt; &amp;amp; </stringConstant>\n" + "</tokens>\n",
        writer.toXml(result));
  }

  @Test
  void preservesWhitespaceInsideStringConstants() {
    TokenizationResult result = resultWithString(" a\tb ");

    assertEquals(
        "<tokens>\n" + "<stringConstant>  a\tb  </stringConstant>\n" + "</tokens>\n",
        writer.toXml(result));
  }

  @Test
  void preservesUnicodeAndSupplementaryCharacters() {
    TokenizationResult result = resultWithString("Olá 😀");

    String xml = writer.toXml(result);

    assertTrue(xml.contains("<stringConstant> Olá 😀 </stringConstant>"));
  }

  @Test
  void producesXmlReadableByStandardJavaParser() throws Exception {
    TokenizationResult result = resultWithString("&<>\"'");

    String xml = writer.toXml(result);

    var factory = DocumentBuilderFactory.newInstance();
    var builder = factory.newDocumentBuilder();
    var document = builder.parse(new InputSource(new StringReader(xml)));

    assertEquals("tokens", document.getDocumentElement().getTagName());
    assertEquals(
        " &<>\"' ", document.getElementsByTagName("stringConstant").item(0).getTextContent());
  }

  @Test
  void serializesCompleteTokenizerOutput() {
    String source = "class Main { let x = 5 < 6; }";

    TokenizationResult result = new JackTokenizer().tokenize(source);

    assertEquals(
        "<tokens>\n"
            + "<keyword> class </keyword>\n"
            + "<identifier> Main </identifier>\n"
            + "<symbol> { </symbol>\n"
            + "<keyword> let </keyword>\n"
            + "<identifier> x </identifier>\n"
            + "<symbol> = </symbol>\n"
            + "<integerConstant> 5 </integerConstant>\n"
            + "<symbol> &lt; </symbol>\n"
            + "<integerConstant> 6 </integerConstant>\n"
            + "<symbol> ; </symbol>\n"
            + "<symbol> } </symbol>\n"
            + "</tokens>\n",
        writer.toXml(result));
  }

  @Test
  void excludesCommentsAndWhitespaceFromOutput() {
    TokenizationResult result =
        new JackTokenizer().tokenize("/* heading */ class A // comment\n{}");

    String xml = writer.toXml(result);

    assertEquals(
        "<tokens>\n"
            + "<keyword> class </keyword>\n"
            + "<identifier> A </identifier>\n"
            + "<symbol> { </symbol>\n"
            + "<symbol> } </symbol>\n"
            + "</tokens>\n",
        xml);
  }

  @Test
  void doesNotIncludeSourcePositionsInXml() {
    TokenizationResult result =
        new TokenizationResult(
            List.of(new Token(TokenType.IDENTIFIER, "value", new SourcePosition(12, 34))),
            new SourcePosition(99, 99));

    assertEquals("<tokens>\n<identifier> value </identifier>\n</tokens>\n", writer.toXml(result));
  }

  @ParameterizedTest
  @ValueSource(ints = {0x0, 0x1, 0x8, 0xB, 0xC, 0x1F, 0xD800, 0xDC00, 0xFFFE, 0xFFFF})
  void rejectsCharactersForbiddenByXml(int codePoint) {
    String invalidCharacter = Character.toString(codePoint);
    TokenizationResult result = resultWithString(invalidCharacter);

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> writer.toXml(result));

    assertTrue(error.getMessage().contains("Invalid XML character"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"<", ">", "&"})
  void escapesJackSymbolsThatRequireXmlEntities(String symbol) {
    TokenizationResult result =
        new TokenizationResult(
            List.of(new Token(TokenType.SYMBOL, symbol, new SourcePosition(1, 1))),
            new SourcePosition(1, 2));

    String xml = writer.toXml(result);

    String escaped =
        switch (symbol) {
          case "<" -> "&lt;";
          case ">" -> "&gt;";
          case "&" -> "&amp;";
          default -> throw new AssertionError("Unexpected symbol.");
        };

    assertEquals("<tokens>\n<symbol> " + escaped + " </symbol>\n</tokens>\n", xml);
  }

  @Test
  void rejectsNullResult() {
    assertThrows(NullPointerException.class, () -> writer.toXml(null));
  }

  @Test
  void producesDeterministicOutputOnRepeatedCalls() {
    TokenizationResult result = new JackTokenizer().tokenize("let n = 00123;");

    String first = writer.toXml(result);
    String second = writer.toXml(result);

    assertEquals(first, second);
    assertTrue(first.contains("<integerConstant> 00123 </integerConstant>"));
  }

  @Test
  void alwaysUsesLfLineEndings() {
    TokenizationResult result = new JackTokenizer().tokenize("class\r\nMain\r\n{}");

    String xml = writer.toXml(result);

    assertFalse(xml.contains("\r"));
    assertTrue(xml.endsWith("</tokens>\n"));
  }

  @Test
  void doesNotModifyTheTokenizationResult() {
    TokenizationResult result = new JackTokenizer().tokenize("class Main {}");

    List<Token> originalTokens = List.copyOf(result.tokens());
    SourcePosition originalEnd = result.endPosition();

    writer.toXml(result);

    assertEquals(originalTokens, result.tokens());
    assertEquals(originalEnd, result.endPosition());
  }

  @ParameterizedTest
  @MethodSource("formattedTerminals")
  void formatsStandaloneTerminalsForFutureSyntaxXmlWriter(
      TokenType type, String value, String expected) {
    Token token = new Token(type, value, new SourcePosition(2, 5));

    String formatted = TokenXmlWriter.formatToken(token);

    assertEquals(expected, formatted);
    assertFalse(formatted.contains("\n"));
    assertFalse(formatted.contains("\r"));
  }

  @Test
  void rejectsNullTokenWhenFormattingStandaloneTerminal() {
    assertThrows(NullPointerException.class, () -> TokenXmlWriter.formatToken(null));
  }

  @Test
  void rejectsInvalidXmlCharacterWhenFormattingStandaloneTerminal() {
    Token token = new Token(TokenType.STRING_CONSTANT, "a\u0001b", new SourcePosition(1, 1));

    assertThrows(IllegalArgumentException.class, () -> TokenXmlWriter.formatToken(token));
  }

  private static Stream<Arguments> formattedTerminals() {
    return Stream.of(
        Arguments.of(TokenType.KEYWORD, "class", "<keyword> class </keyword>"),
        Arguments.of(TokenType.IDENTIFIER, "Main", "<identifier> Main </identifier>"),
        Arguments.of(TokenType.SYMBOL, "<", "<symbol> &lt; </symbol>"),
        Arguments.of(
            TokenType.INTEGER_CONSTANT, "00042", "<integerConstant> 00042 </integerConstant>"),
        Arguments.of(
            TokenType.STRING_CONSTANT,
            "a&<>\"'",
            "<stringConstant> a&amp;&lt;&gt;&quot;&apos; </stringConstant>"),
        Arguments.of(TokenType.STRING_CONSTANT, "", "<stringConstant>  </stringConstant>"));
  }

  private static TokenizationResult resultWithString(String value) {
    return new TokenizationResult(
        List.of(new Token(TokenType.STRING_CONSTANT, value, new SourcePosition(1, 1))),
        new SourcePosition(1, 2));
  }
}
