package jackcompiler.acceptance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.application.TokenizeCommand;
import jackcompiler.lexer.JackTokenizer;
import jackcompiler.xml.TokenXmlWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Acceptance tests against unmodified nand2tetris Project 10 token XML references. */
class OfficialTokenXmlCompatibilityTest {

  private static final String ROOT = "/nand2tetris/";

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

  @ParameterizedTest(name = "{0}/{1}: exact tokens")
  @MethodSource("examples")
  void tokenizerMatchesOfficialXmlWithOnlyLineEndingNormalization(String suite, String name)
      throws IOException {
    String source = readText(suite, name + ".jack");
    String reference = normalizeLineEndings(readText(suite, name + "T.xml"));

    String actual = new TokenXmlWriter().toXml(new JackTokenizer().tokenize(source));

    assertEquals(reference, actual, suite + "/" + name);
  }

  @ParameterizedTest(name = "{0}/{1}: complete application")
  @MethodSource("examples")
  void applicationMatchesOfficialXmlAndPreservesSource(String suite, String name) throws Exception {
    Path source = tempDir.resolve(suite + "_" + name + ".jack");
    byte[] original = readBytes(suite, name + ".jack");
    Files.write(source, original);

    Path directory = tempDir.resolve("generated").resolve(suite);
    List<Path> outputs = new TokenizeCommand().execute(source, Optional.of(directory));
    Path output = directory.resolve(suite + "_" + name + "T.xml");

    assertEquals(List.of(output), outputs);
    assertEquals(
        normalizeLineEndings(readText(suite, name + "T.xml")),
        Files.readString(output, StandardCharsets.UTF_8),
        suite + "/" + name);
    assertArrayEquals(original, Files.readAllBytes(source));
  }

  @Test
  void preservesSpacesWithinOfficialStringConstants() throws IOException {
    String reference = normalizeLineEndings(readText("ArrayTest", "MainT.xml"));
    String source = readText("ArrayTest", "Main.jack");
    String actual = new TokenXmlWriter().toXml(new JackTokenizer().tokenize(source));

    assertTrue(reference.contains("<stringConstant> HOW MANY NUMBERS?  </stringConstant>"));
    assertEquals(reference, actual);
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
        OfficialTokenXmlCompatibilityTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("Official reference missing: " + resource);
      }
      return input.readAllBytes();
    }
  }

  private static String normalizeLineEndings(String value) {
    return value.replace("\r\n", "\n").replace('\r', '\n');
  }

  private record Example(String suite, String name) {}
}
