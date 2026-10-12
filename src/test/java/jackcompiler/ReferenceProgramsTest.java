package jackcompiler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.xml.TokenXmlWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Validates Jack tokenization against the nand2tetris Project 10 fixtures.
 *
 * <p>Reference material comes from nand2tetris/projects, revision
 * 6097b770073025891fb8b7a22aa3b72f61f81277. The upstream MIT license is stored with the test
 * resources.
 */
class ReferenceProgramsTest {

  private static final String RESOURCE_ROOT = "/jackcompiler/reference/";

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

  @ParameterizedTest(name = "{0}/{1}: tokenizer")
  @MethodSource("examples")
  void tokenizationMatchesOfficialReference(String suite, String name) throws IOException {
    String source = readText(suite, name + ".jack");
    String expected = normalizeLineEndings(readText(suite, name + "T.xml"));

    var tokens = new JackTokenizer().tokenize(source);
    String actual = new TokenXmlWriter().toXml(tokens);

    assertFalse(tokens.tokens().isEmpty());
    assertEquals(expected, actual, suite + "/" + name);
  }

  @ParameterizedTest(name = "{0}/{1}: CLI")
  @MethodSource("examples")
  void commandLineMatchesOfficialReference(String suite, String name) throws IOException {
    Path directory = Files.createDirectory(tempDir.resolve(suite));
    Path source = directory.resolve(name + ".jack");

    byte[] original = readBytes(suite, name + ".jack");
    Files.write(source, original);

    Execution execution = execute("tokenize", source.toString());

    Path output = directory.resolve(name + "T.xml");

    assertEquals(0, execution.status());
    assertEquals("", execution.stderr());
    assertEquals("Generated: " + output + "\n", execution.stdout());
    assertTrue(Files.isRegularFile(output));

    String expected = normalizeLineEndings(readText(suite, name + "T.xml"));
    String actual = normalizeLineEndings(Files.readString(output, StandardCharsets.UTF_8));

    assertEquals(expected, actual, suite + "/" + name);
    assertArrayEquals(original, Files.readAllBytes(source));
  }

  @Test
  void processesAllSevenReferenceProgramsInOneDirectory() throws IOException {
    Map<Path, String> expectedOutputs = new HashMap<>();

    for (Example example : EXAMPLES) {
      String stem = example.suite() + "_" + example.name();
      Path source = tempDir.resolve(stem + ".jack");
      Path output = tempDir.resolve(stem + "T.xml");

      Files.write(source, readBytes(example.suite(), example.name() + ".jack"));

      expectedOutputs.put(
          output, normalizeLineEndings(readText(example.suite(), example.name() + "T.xml")));
    }

    Execution execution = execute("tokenize", tempDir.toString());

    assertEquals(0, execution.status());
    assertEquals("", execution.stderr());

    // Output order follows source-file names, not generated XML names.
    List<Path> orderedOutputs =
        EXAMPLES.stream()
            .map(example -> example.suite() + "_" + example.name())
            .sorted()
            .map(stem -> tempDir.resolve(stem + "T.xml"))
            .toList();

    String expectedConsoleOutput =
        orderedOutputs.stream()
            .map(path -> "Generated: " + path + "\n")
            .collect(Collectors.joining());

    assertEquals(expectedConsoleOutput, execution.stdout());
    assertEquals(7, orderedOutputs.size());

    for (Path output : orderedOutputs) {
      assertTrue(Files.isRegularFile(output));

      String actual = normalizeLineEndings(Files.readString(output, StandardCharsets.UTF_8));

      assertEquals(expectedOutputs.get(output), actual, output.getFileName().toString());
    }
  }

  private static Stream<Arguments> examples() {
    return EXAMPLES.stream().map(example -> Arguments.of(example.suite(), example.name()));
  }

  private static String readText(String suite, String name) throws IOException {
    return new String(readBytes(suite, name), StandardCharsets.UTF_8);
  }

  private static byte[] readBytes(String suite, String name) throws IOException {
    String resource = RESOURCE_ROOT + suite + "/" + name;

    try (InputStream input = ReferenceProgramsTest.class.getResourceAsStream(resource)) {

      if (input == null) {
        throw new IOException("Missing reference resource: " + resource);
      }

      return input.readAllBytes();
    }
  }

  private static String normalizeLineEndings(String text) {
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static Execution execute(String... arguments) {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {

      int status = Main.run(arguments, out, err);

      return new Execution(
          status, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }
  }

  private record Example(String suite, String name) {}

  private record Execution(int status, String stdout, String stderr) {}
}
