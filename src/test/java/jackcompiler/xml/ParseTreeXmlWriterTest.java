package jackcompiler.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.parser.JackParser;
import jackcompiler.syntax.GrammarNode;
import jackcompiler.syntax.ParseNode;
import jackcompiler.syntax.SyntaxTag;
import jackcompiler.syntax.TerminalNode;
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

class ParseTreeXmlWriterTest {

  private final ParseTreeXmlWriter writer = new ParseTreeXmlWriter();

  @Test
  void rejectsNullRoot() {
    assertThrows(NullPointerException.class, () -> writer.toXml(null));
  }

  @Test
  void writesEmptyGrammarNodeWithExplicitClosingTag() {
    GrammarNode root = tree(SyntaxTag.CLASS);

    assertEquals("<class>\n</class>\n", writer.toXml(root));
  }

  @Test
  void serializesMinimalClassExactly() {
    GrammarNode root = parse("class Main {}");

    assertEquals(
        "<class>\n"
            + "  <keyword> class </keyword>\n"
            + "  <identifier> Main </identifier>\n"
            + "  <symbol> { </symbol>\n"
            + "  <symbol> } </symbol>\n"
            + "</class>\n",
        writer.toXml(root));
  }

  @Test
  void serializesFunctionWithEmptyParameterListAndStatementsExactly() {
    GrammarNode root = parse("class A { function void f() {} }");

    assertEquals(
        "<class>\n"
            + "  <keyword> class </keyword>\n"
            + "  <identifier> A </identifier>\n"
            + "  <symbol> { </symbol>\n"
            + "  <subroutineDec>\n"
            + "    <keyword> function </keyword>\n"
            + "    <keyword> void </keyword>\n"
            + "    <identifier> f </identifier>\n"
            + "    <symbol> ( </symbol>\n"
            + "    <parameterList>\n"
            + "    </parameterList>\n"
            + "    <symbol> ) </symbol>\n"
            + "    <subroutineBody>\n"
            + "      <symbol> { </symbol>\n"
            + "      <statements>\n"
            + "      </statements>\n"
            + "      <symbol> } </symbol>\n"
            + "    </subroutineBody>\n"
            + "  </subroutineDec>\n"
            + "  <symbol> } </symbol>\n"
            + "</class>\n",
        writer.toXml(root));
  }

  @ParameterizedTest
  @MethodSource("grammarTags")
  void mapsEveryOfficialGrammarCategory(SyntaxTag tag, String xmlTag) {
    GrammarNode root = tree(tag);

    assertEquals("<" + xmlTag + ">\n</" + xmlTag + ">\n", writer.toXml(root));
  }

  @Test
  void serializesAllFiveTerminalCategories() {
    GrammarNode root =
        tree(
            SyntaxTag.CLASS,
            terminal(TokenType.KEYWORD, "class"),
            terminal(TokenType.IDENTIFIER, "Main"),
            terminal(TokenType.SYMBOL, "{"),
            terminal(TokenType.INTEGER_CONSTANT, "32767"),
            terminal(TokenType.STRING_CONSTANT, "hello"));

    assertEquals(
        "<class>\n"
            + "  <keyword> class </keyword>\n"
            + "  <identifier> Main </identifier>\n"
            + "  <symbol> { </symbol>\n"
            + "  <integerConstant> 32767 </integerConstant>\n"
            + "  <stringConstant> hello </stringConstant>\n"
            + "</class>\n",
        writer.toXml(root));
  }

  @Test
  void reusesExistingTerminalFormattingWithoutChangingIt() {
    Token token = new Token(TokenType.STRING_CONSTANT, " a&b ", new SourcePosition(3, 7));
    GrammarNode root = tree(SyntaxTag.EXPRESSION, new TerminalNode(token));

    assertEquals(
        "<expression>\n" + "  " + TokenXmlWriter.formatToken(token) + "\n" + "</expression>\n",
        writer.toXml(root));
  }

  @Test
  void escapesAllXmlReservedCharacters() {
    GrammarNode root = tree(SyntaxTag.TERM, terminal(TokenType.STRING_CONSTANT, "&<>\"'"));

    assertEquals(
        "<term>\n"
            + "  <stringConstant> &amp;&lt;&gt;&quot;&apos; </stringConstant>\n"
            + "</term>\n",
        writer.toXml(root));
  }

  @Test
  void doesNotTreatEntityLikeSourceTextAsAlreadyEscaped() {
    GrammarNode root = tree(SyntaxTag.TERM, terminal(TokenType.STRING_CONSTANT, "&lt; &amp;"));

    assertEquals(
        "<term>\n" + "  <stringConstant> &amp;lt; &amp;amp; </stringConstant>\n" + "</term>\n",
        writer.toXml(root));
  }

  @Test
  void preservesSignificantWhitespaceInsideStringConstants() {
    GrammarNode root = tree(SyntaxTag.TERM, terminal(TokenType.STRING_CONSTANT, "  a\tb  "));

    assertEquals(
        "<term>\n" + "  <stringConstant>   a\tb   </stringConstant>\n" + "</term>\n",
        writer.toXml(root));
  }

  @Test
  void preservesUnicodeAndSupplementaryCharacters() {
    GrammarNode root = tree(SyntaxTag.TERM, terminal(TokenType.STRING_CONSTANT, "Olá 😀"));

    assertEquals(
        "<term>\n" + "  <stringConstant> Olá 😀 </stringConstant>\n" + "</term>\n",
        writer.toXml(root));
  }

  @Test
  void preservesOriginalIntegerLexemeIncludingLeadingZeros() {
    GrammarNode root = tree(SyntaxTag.TERM, terminal(TokenType.INTEGER_CONSTANT, "00042"));

    assertEquals(
        "<term>\n" + "  <integerConstant> 00042 </integerConstant>\n" + "</term>\n",
        writer.toXml(root));
  }

  @Test
  void escapesComparisonAndLogicalOperatorsInParsedExpressions() {
    GrammarNode root =
        parse(
            "class Main {"
                + " function boolean test(int x) {"
                + " return x < 3 & x > 0;"
                + " }"
                + "}");

    String xml = writer.toXml(root);

    assertTrue(xml.contains("<symbol> &lt; </symbol>"));
    assertTrue(xml.contains("<symbol> &amp; </symbol>"));
    assertTrue(xml.contains("<symbol> &gt; </symbol>"));
    assertTrue(xml.contains("<returnStatement>"));
    assertTrue(xml.contains("<expression>"));
  }

  @Test
  void doesNotCreateNonstandardGrammarWrappers() {
    GrammarNode root =
        parse(
            "class Main {"
                + " function void main() {"
                + " do Output.printInt(1);"
                + " return;"
                + " }"
                + "}");

    String xml = writer.toXml(root);

    assertTrue(xml.contains("<doStatement>"));
    assertTrue(xml.contains("<expressionList>"));
    assertTrue(xml.contains("<term>"));
    assertFalse(xml.contains("<statement>"));
    assertFalse(xml.contains("<subroutineCall>"));
    assertFalse(xml.contains("<op>"));
    assertFalse(xml.contains("<unaryOp>"));
    assertFalse(xml.contains("<keywordConstant>"));
    assertFalse(xml.contains("<type>"));
  }

  @Test
  void preservesEmptyParameterAndExpressionListsAsExplicitElements() {
    GrammarNode root =
        parse("class Main {" + " function void run() {" + " do action();" + " }" + "}");

    String xml = writer.toXml(root);

    assertTrue(xml.contains("<parameterList>\n    </parameterList>\n"));
    assertTrue(xml.contains("<expressionList>\n          </expressionList>\n"));
    assertFalse(xml.contains("<parameterList/>"));
    assertFalse(xml.contains("<expressionList/>"));
  }

  @Test
  void serializesNestedIfElseAndWhileWithCorrectIndentation() {
    GrammarNode root =
        parse(
            "class Main {"
                + " function void run() {"
                + " if (true) { while (false) { return; } }"
                + " else { do action(); }"
                + " }"
                + "}");

    String xml = writer.toXml(root);

    assertTrue(xml.contains("        <ifStatement>\n"));
    assertTrue(xml.contains("          <statements>\n            <whileStatement>\n"));
    assertTrue(xml.contains("        </ifStatement>\n"));
    assertTrue(xml.indexOf("<whileStatement>") > xml.indexOf("<ifStatement>"));
    assertTrue(xml.indexOf("<doStatement>") > xml.indexOf("<whileStatement>"));
    assertTrue(xml.endsWith("</class>\n"));
  }

  @Test
  void producesWellFormedXmlWithDecodedStringContent() throws Exception {
    GrammarNode root =
        parse(
            "class Main {"
                + " function void run() {"
                + " do Output.printString(\" a & < b \");"
                + " return;"
                + " }"
                + "}");

    String xml = writer.toXml(root);
    var document = parseXml(xml);

    assertEquals("class", document.getDocumentElement().getTagName());
    assertEquals(1, document.getElementsByTagName("stringConstant").getLength());
    assertEquals(
        "  a & < b  ", document.getElementsByTagName("stringConstant").item(0).getTextContent());
  }

  @Test
  void doesNotEmitXmlDeclarationOrLexicalTokensWrapper() {
    GrammarNode root = parse("class Main {}");

    String xml = writer.toXml(root);

    assertTrue(xml.startsWith("<class>\n"));
    assertFalse(xml.contains("<?xml"));
    assertFalse(xml.contains("<tokens>"));
    assertFalse(xml.contains("</tokens>"));
  }

  @Test
  void usesLfLineEndingsForSourceContainingCrLf() {
    GrammarNode root = parse("class Main {\r\n function void run() {\r\n return;\r\n }\r\n}");

    String xml = writer.toXml(root);

    assertFalse(xml.contains("\r"));
    assertTrue(xml.endsWith("</class>\n"));
  }

  @Test
  void producesDeterministicOutputOnRepeatedCalls() {
    GrammarNode root = parse("class Main { function int value() { return 3; } }");

    String first = writer.toXml(root);
    String second = writer.toXml(root);

    assertEquals(first, second);
  }

  @Test
  void remainsReusableAcrossDifferentTrees() {
    GrammarNode first = parse("class First {}");
    GrammarNode second = parse("class Second { function void run() {} }");

    String firstXml = writer.toXml(first);
    String secondXml = writer.toXml(second);

    assertTrue(firstXml.contains("<identifier> First </identifier>"));
    assertTrue(secondXml.contains("<identifier> Second </identifier>"));
    assertFalse(secondXml.contains("<identifier> First </identifier>"));
    assertEquals(firstXml, writer.toXml(first));
  }

  @Test
  void doesNotModifyTreeOrOriginalLexicalResult() {
    TokenizationResult result = new JackTokenizer().tokenize("class Main { field int x; }");
    GrammarNode root = new JackParser().parse(result);

    List<Token> originalTokens = List.copyOf(result.tokens());
    SourcePosition originalEnd = result.endPosition();
    List<ParseNode> originalChildren = root.children();

    writer.toXml(root);

    assertEquals(originalTokens, result.tokens());
    assertEquals(originalEnd, result.endPosition());
    assertSame(originalChildren, root.children());
    assertEquals(SyntaxTag.CLASS, root.tag());
    assertThrows(
        UnsupportedOperationException.class,
        () -> root.children().add(terminal(TokenType.SYMBOL, ";")));
  }

  @ParameterizedTest
  @ValueSource(ints = {0x0, 0x1, 0x8, 0xB, 0xC, 0x1F, 0xD800, 0xDC00, 0xFFFE, 0xFFFF})
  void rejectsCharactersForbiddenByXml10(int codePoint) {
    GrammarNode root =
        tree(SyntaxTag.TERM, terminal(TokenType.STRING_CONSTANT, Character.toString(codePoint)));

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> writer.toXml(root));

    assertTrue(error.getMessage().contains("Invalid XML character"));
  }

  @Test
  void rejectsInvalidXmlCharacterInsideNestedTree() {
    GrammarNode root =
        tree(
            SyntaxTag.CLASS,
            tree(
                SyntaxTag.SUBROUTINE_DEC,
                tree(SyntaxTag.TERM, terminal(TokenType.STRING_CONSTANT, "valid\u0001invalid"))));

    assertThrows(IllegalArgumentException.class, () -> writer.toXml(root));
  }

  private static GrammarNode parse(String source) {
    TokenizationResult result = new JackTokenizer().tokenize(source);
    return new JackParser().parse(result);
  }

  private static GrammarNode tree(SyntaxTag tag, ParseNode... children) {
    return new GrammarNode(tag, List.of(children));
  }

  private static TerminalNode terminal(TokenType type, String value) {
    return new TerminalNode(new Token(type, value, new SourcePosition(1, 1)));
  }

  private static org.w3c.dom.Document parseXml(String xml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);

    return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
  }

  private static Stream<Arguments> grammarTags() {
    return Stream.of(
        Arguments.of(SyntaxTag.CLASS, "class"),
        Arguments.of(SyntaxTag.CLASS_VAR_DEC, "classVarDec"),
        Arguments.of(SyntaxTag.SUBROUTINE_DEC, "subroutineDec"),
        Arguments.of(SyntaxTag.PARAMETER_LIST, "parameterList"),
        Arguments.of(SyntaxTag.SUBROUTINE_BODY, "subroutineBody"),
        Arguments.of(SyntaxTag.VAR_DEC, "varDec"),
        Arguments.of(SyntaxTag.STATEMENTS, "statements"),
        Arguments.of(SyntaxTag.LET_STATEMENT, "letStatement"),
        Arguments.of(SyntaxTag.IF_STATEMENT, "ifStatement"),
        Arguments.of(SyntaxTag.WHILE_STATEMENT, "whileStatement"),
        Arguments.of(SyntaxTag.DO_STATEMENT, "doStatement"),
        Arguments.of(SyntaxTag.RETURN_STATEMENT, "returnStatement"),
        Arguments.of(SyntaxTag.EXPRESSION, "expression"),
        Arguments.of(SyntaxTag.TERM, "term"),
        Arguments.of(SyntaxTag.EXPRESSION_LIST, "expressionList"));
  }
}
