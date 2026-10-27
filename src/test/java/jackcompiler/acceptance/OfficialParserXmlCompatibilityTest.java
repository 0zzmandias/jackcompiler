package jackcompiler.acceptance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.application.ParseCommand;
import jackcompiler.lexer.JackTokenizer;
import jackcompiler.parser.JackParser;
import jackcompiler.xml.ParseTreeXmlWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentest4j.AssertionFailedError;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Acceptance tests against the official nand2tetris Project 10 syntax XML references.
 *
 * <p>Fixtures originate from nand2tetris/projects, revision
 * 6097b770073025891fb8b7a22aa3b72f61f81277. The upstream MIT license is preserved with the test
 * resources.
 *
 * <p>Comparison preserves grammar hierarchy, child order and terminal values. Only indentation
 * whitespace between grammar elements and the conventional single padding space around terminal
 * values are excluded from the comparison.
 */
class OfficialParserXmlCompatibilityTest {

  private static final String ROOT = "/nand2tetris/";

  private static final Set<String> TERMINAL_TAGS =
      Set.of("keyword", "symbol", "identifier", "integerConstant", "stringConstant");

  private static final List<Example> EXAMPLES =
      List.of(
          new Example("Square", "Main"),
          new Example("Square", "Square"),
          new Example("Square", "SquareGame"),
          new Example("ExpressionLessSquare", "Main"),
          new Example("ExpressionLessSquare", "Square"),
          new Example("ExpressionLessSquare", "SquareGame"),
          new Example("ArrayTest", "Main"));

  @TempDir Path tempDir;

  @ParameterizedTest(name = "{0}/{1}: parser and XML writer")
  @MethodSource("examples")
  void parserMatchesOfficialSyntaxTree(String suite, String name) throws Exception {
    String source = readText(suite, name + ".jack");
    String expected = readText(suite, name + ".xml");

    String actual =
        new ParseTreeXmlWriter()
            .toXml(new JackParser().parse(new JackTokenizer().tokenize(source)));

    assertEquivalentXml(expected, actual, suite + "/" + name);
  }

  @ParameterizedTest(name = "{0}/{1}: application output")
  @MethodSource("examples")
  void applicationMatchesOfficialSyntaxTreeAndPreservesSource(String suite, String name)
      throws Exception {
    Path sourceDirectory = Files.createDirectory(tempDir.resolve(suite));
    Path source = sourceDirectory.resolve(name + ".jack");
    Path outputDirectory = sourceDirectory.resolve("generated");

    byte[] original = readBytes(suite, name + ".jack");
    Files.write(source, original);

    List<Path> outputs = new ParseCommand().execute(source, Optional.of(outputDirectory));

    Path expectedOutput = outputDirectory.resolve(name + "P.xml");

    assertEquals(List.of(expectedOutput), outputs);
    assertTrue(Files.isRegularFile(expectedOutput));
    assertFalse(Files.exists(outputDirectory.resolve(name + "T.xml")));

    String reference = readText(suite, name + ".xml");
    String actual = Files.readString(expectedOutput, StandardCharsets.UTF_8);

    assertEquivalentXml(reference, actual, suite + "/" + name);
    assertArrayEquals(original, Files.readAllBytes(source));
  }

  @Test
  void preservesSignificantWhitespaceInOfficialStringConstants() throws Exception {
    String source = readText("ArrayTest", "Main.jack");
    String reference = readText("ArrayTest", "Main.xml");

    String actual =
        new ParseTreeXmlWriter()
            .toXml(new JackParser().parse(new JackTokenizer().tokenize(source)));

    assertTrue(reference.contains("<stringConstant> HOW MANY NUMBERS?  </stringConstant>"));
    assertTrue(actual.contains("<stringConstant> HOW MANY NUMBERS?  </stringConstant>"));

    assertEquivalentXml(reference, actual, "ArrayTest/Main");
  }

  @Test
  void ignoresFormattingWhitespaceAndEquivalentEntityEncodings() throws Exception {
    String reference =
        "<class>\r\n"
            + "  <term>\r\n"
            + "    <symbol> &lt; </symbol>\r\n"
            + "  </term>\r\n"
            + "</class>\r\n";

    String actual = "<class><term><symbol> &#60; </symbol></term></class>";

    assertEquivalentXml(reference, actual, "formatting");
  }

  @Test
  void rejectsDifferentGrammarHierarchy() {
    String reference =
        "<class><expression><term><identifier> x </identifier></term></expression></class>";

    String actual = "<class><term><identifier> x </identifier></term></class>";

    assertThrows(
        AssertionFailedError.class, () -> assertEquivalentXml(reference, actual, "hierarchy"));
  }

  @Test
  void rejectsDifferentChildOrder() {
    String reference =
        "<class>" + "<keyword> class </keyword>" + "<identifier> Main </identifier>" + "</class>";

    String actual =
        "<class>" + "<identifier> Main </identifier>" + "<keyword> class </keyword>" + "</class>";

    assertThrows(AssertionFailedError.class, () -> assertEquivalentXml(reference, actual, "order"));
  }

  @Test
  void rejectsDifferentTerminalValues() {
    String reference = "<class><identifier> Main </identifier></class>";

    String actual = "<class><identifier> Other </identifier></class>";

    assertThrows(
        AssertionFailedError.class, () -> assertEquivalentXml(reference, actual, "values"));
  }

  @Test
  void doesNotDiscardSpacesBelongingToStringValues() {
    String reference = "<class><stringConstant>  text  </stringConstant></class>";

    String actual = "<class><stringConstant> text </stringConstant></class>";

    assertThrows(
        AssertionFailedError.class,
        () -> assertEquivalentXml(reference, actual, "string whitespace"));
  }

  @Test
  void rejectsMissingGrammarChildren() {
    String reference =
        "<class>" + "<keyword> class </keyword>" + "<identifier> Main </identifier>" + "</class>";

    String actual = "<class><keyword> class </keyword></class>";

    assertThrows(
        AssertionFailedError.class, () -> assertEquivalentXml(reference, actual, "missing child"));
  }

  @Test
  void rejectsUnexpectedTextInsideGrammarElements() {
    String reference = "<class><identifier> Main </identifier></class>";

    String actual = "<class>unexpected<identifier> Main </identifier></class>";

    assertThrows(
        AssertionFailedError.class, () -> assertEquivalentXml(reference, actual, "grammar text"));
  }

  @Test
  void rejectsTerminalValuesWithoutConventionalPadding() {
    String reference = "<class><identifier> Main </identifier></class>";

    String actual = "<class><identifier>Main</identifier></class>";

    assertThrows(
        AssertionFailedError.class, () -> assertEquivalentXml(reference, actual, "padding"));
  }

  private static void assertEquivalentXml(String reference, String actual, String label)
      throws Exception {
    Document expectedDocument = parseXml(reference);
    Document actualDocument = parseXml(actual);

    assertEquivalentElement(
        expectedDocument.getDocumentElement(), actualDocument.getDocumentElement(), label);
  }

  private static Document parseXml(String xml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    factory.setNamespaceAware(false);

    return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
  }

  private static void assertEquivalentElement(Element expected, Element actual, String path) {

    assertEquals(expected.getTagName(), actual.getTagName(), path + ": element name");

    assertEquals(
        0, expected.getAttributes().getLength(), path + ": unexpected reference attributes");

    assertEquals(0, actual.getAttributes().getLength(), path + ": unexpected generated attributes");

    if (TERMINAL_TAGS.contains(expected.getTagName())) {
      String expectedValue = terminalValue(expected, path);
      String actualValue = terminalValue(actual, path);

      assertEquals(expectedValue, actualValue, path + ": terminal value");

      return;
    }

    List<Element> expectedChildren = grammarChildren(expected, path);
    List<Element> actualChildren = grammarChildren(actual, path);

    assertEquals(expectedChildren.size(), actualChildren.size(), path + ": grammar child count");

    for (int index = 0; index < expectedChildren.size(); index++) {
      Element expectedChild = expectedChildren.get(index);
      Element actualChild = actualChildren.get(index);

      assertEquivalentElement(
          expectedChild, actualChild, path + "/" + expectedChild.getTagName() + "[" + index + "]");
    }
  }

  private static String terminalValue(Element terminal, String path) {
    NodeList children = terminal.getChildNodes();

    for (int index = 0; index < children.getLength(); index++) {
      Node child = children.item(index);

      assertTrue(
          child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE,
          path + ": terminal must contain only text");
    }

    String text = terminal.getTextContent();

    assertTrue(
        text.length() >= 2 && text.charAt(0) == ' ' && text.charAt(text.length() - 1) == ' ',
        path + ": missing conventional terminal padding");

    return text.substring(1, text.length() - 1);
  }

  private static List<Element> grammarChildren(Element grammar, String path) {
    List<Element> children = new ArrayList<>();
    NodeList nodes = grammar.getChildNodes();

    for (int index = 0; index < nodes.getLength(); index++) {
      Node node = nodes.item(index);

      if (node.getNodeType() == Node.ELEMENT_NODE) {
        children.add((Element) node);
      } else if (node.getNodeType() == Node.TEXT_NODE) {
        assertTrue(
            node.getTextContent().isBlank(), path + ": unexpected text inside grammar element");
      } else {
        throw new AssertionFailedError(path + ": unexpected XML node type " + node.getNodeType());
      }
    }

    return children;
  }

  private static Stream<Arguments> examples() {
    return EXAMPLES.stream().map(example -> Arguments.of(example.suite(), example.name()));
  }

  private static String readText(String suite, String name) throws IOException {
    return new String(readBytes(suite, name), StandardCharsets.UTF_8);
  }

  private static byte[] readBytes(String suite, String name) throws IOException {
    String resource = ROOT + suite + "/" + name;

    try (InputStream input =
        OfficialParserXmlCompatibilityTest.class.getResourceAsStream(resource)) {

      if (input == null) {
        throw new IOException("Official reference missing: " + resource);
      }

      return input.readAllBytes();
    }
  }

  private record Example(String suite, String name) {}
}
