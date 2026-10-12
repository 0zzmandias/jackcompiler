package jackcompiler.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.xml.TokenXmlWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TokenXmlFilesTest {

  @TempDir Path tempDir;

  private final TokenXmlFiles xmlFiles = new TokenXmlFiles();

  @Test
  void derivesNand2TetrisTokenXmlName() {
    Path source = tempDir.resolve("Main.jack");

    assertEquals(tempDir.resolve("MainT.xml"), xmlFiles.outputPath(source));
  }

  @Test
  void retainsSourceDirectory() throws IOException {
    Path nested = Files.createDirectory(tempDir.resolve("project"));
    Path source = nested.resolve("Main.jack");

    assertEquals(nested.resolve("MainT.xml"), xmlFiles.outputPath(source));
  }

  @Test
  void preservesAdditionalDotsInBaseName() {
    Path source = tempDir.resolve("Example.test.jack");

    assertEquals(tempDir.resolve("Example.testT.xml"), xmlFiles.outputPath(source));
  }

  @Test
  void handlesUnicodeAndSpacesInFileName() {
    Path source = tempDir.resolve("Olá Mundo.jack");

    assertEquals(tempDir.resolve("Olá MundoT.xml"), xmlFiles.outputPath(source));
  }

  @Test
  void normalizesRelativePaths() {
    Path source = Path.of("folder", "..", "Main.jack");
    Path expected = Path.of("MainT.xml").toAbsolutePath().normalize();

    assertEquals(expected, xmlFiles.outputPath(source));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Main.txt", "Main.JACK", "Main.jack.bak", "Main"})
  void rejectsUnsupportedSourceExtensions(String name) {
    Path source = tempDir.resolve(name);

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> xmlFiles.outputPath(source));

    assertTrue(error.getMessage().contains(".jack"));
  }

  @Test
  void rejectsEmptySourceBaseName() {
    Path source = tempDir.resolve(".jack");

    assertThrows(IllegalArgumentException.class, () -> xmlFiles.outputPath(source));
  }

  @Test
  void rejectsNullSourcePath() {
    assertThrows(NullPointerException.class, () -> xmlFiles.outputPath(null));

    assertThrows(NullPointerException.class, () -> xmlFiles.write(null, "<tokens>\n</tokens>\n"));
  }

  @Test
  void writesXmlUsingUtf8WithoutBom() throws IOException {
    Path source = tempDir.resolve("Main.jack");
    String xml = "<tokens>\n" + "<stringConstant> Olá 😀 </stringConstant>\n" + "</tokens>\n";

    Path output = xmlFiles.write(source, xml);

    assertEquals(tempDir.resolve("MainT.xml"), output);
    assertEquals(xml, Files.readString(output, StandardCharsets.UTF_8));
    assertArrayEquals(xml.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(output));
  }

  @Test
  void overwritesPreviouslyGeneratedXml() throws IOException {
    Path source = tempDir.resolve("Main.jack");

    xmlFiles.write(source, "<tokens>\n</tokens>\n");

    Path output = xmlFiles.write(source, "<tokens>\n<keyword> class </keyword>\n</tokens>\n");

    assertEquals("<tokens>\n<keyword> class </keyword>\n</tokens>\n", Files.readString(output));
  }

  @Test
  void preservesOriginalJackSourceFile() throws IOException {
    Path source = tempDir.resolve("Main.jack");
    String original = "class Main {}\r\n";

    Files.writeString(source, original, StandardCharsets.UTF_8);

    xmlFiles.write(source, "<tokens>\n</tokens>\n");

    assertEquals(original, Files.readString(source));
    assertTrue(Files.exists(tempDir.resolve("MainT.xml")));
  }

  @Test
  void writesEmptyTokenDocument() throws IOException {
    Path source = tempDir.resolve("Empty.jack");
    String xml = "<tokens>\n</tokens>\n";

    Path output = xmlFiles.write(source, xml);

    assertEquals(xml, Files.readString(output));
  }

  @Test
  void integratesSourceReadingTokenizationAndXmlWriting() throws IOException {
    Path source = tempDir.resolve("Main.jack");

    Files.writeString(source, "class Main { let x = 5 < 6; }");

    JackSourceReader sourceReader = new JackSourceReader();
    JackTokenizer tokenizer = new JackTokenizer();
    TokenXmlWriter serializer = new TokenXmlWriter();

    String jackSource = sourceReader.read(source);
    String xml = serializer.toXml(tokenizer.tokenize(jackSource));

    Path output = xmlFiles.write(source, xml);

    assertEquals(tempDir.resolve("MainT.xml"), output);
    assertEquals(xml, Files.readString(output));
    assertTrue(xml.contains("<keyword> class </keyword>"));
    assertTrue(xml.contains("<identifier> Main </identifier>"));
    assertTrue(xml.contains("<symbol> &lt; </symbol>"));
  }

  @Test
  void writesDifferentSourcesToDifferentOutputs() throws IOException {
    Path first = tempDir.resolve("Alpha.jack");
    Path second = tempDir.resolve("Beta.jack");

    Path firstOutput = xmlFiles.write(first, "<tokens>\n</tokens>\n");
    Path secondOutput =
        xmlFiles.write(second, "<tokens>\n<keyword> return </keyword>\n</tokens>\n");

    assertEquals(tempDir.resolve("AlphaT.xml"), firstOutput);
    assertEquals(tempDir.resolve("BetaT.xml"), secondOutput);

    assertEquals("<tokens>\n</tokens>\n", Files.readString(firstOutput));
    assertTrue(Files.readString(secondOutput).contains("return"));
  }

  @Test
  void writesIntoExistingNestedDirectory() throws IOException {
    Path nested = Files.createDirectory(tempDir.resolve("src"));
    Path source = nested.resolve("Main.jack");

    Path output = xmlFiles.write(source, "<tokens>\n</tokens>\n");

    assertEquals(nested.resolve("MainT.xml"), output);
    assertTrue(Files.isRegularFile(output));
  }

  @Test
  void doesNotCreateMissingParentDirectories() {
    Path source = tempDir.resolve("missing").resolve("Main.jack");

    assertThrows(IOException.class, () -> xmlFiles.write(source, "<tokens>\n</tokens>\n"));

    assertFalse(Files.exists(tempDir.resolve("missing")));
  }

  @Test
  void propagatesOutputPathCollisionWithDirectory() throws IOException {
    Path source = tempDir.resolve("Main.jack");
    Files.createDirectory(tempDir.resolve("MainT.xml"));

    assertThrows(IOException.class, () -> xmlFiles.write(source, "<tokens>\n</tokens>\n"));
  }

  @Test
  void rejectsNullXmlWithoutCreatingOutput() {
    Path source = tempDir.resolve("Main.jack");

    assertThrows(NullPointerException.class, () -> xmlFiles.write(source, null));

    assertFalse(Files.exists(tempDir.resolve("MainT.xml")));
  }

  @Test
  void rejectsInvalidSourceExtensionBeforeWriting() {
    Path source = tempDir.resolve("Main.txt");

    assertThrows(
        IllegalArgumentException.class, () -> xmlFiles.write(source, "<tokens>\n</tokens>\n"));

    assertFalse(Files.exists(tempDir.resolve("MainT.xml")));
  }

  @Test
  void writingXmlDoesNotCreateOrModifyJackSource() throws IOException {
    Path source = tempDir.resolve("Absent.jack");

    xmlFiles.write(source, "<tokens>\n</tokens>\n");

    assertFalse(Files.exists(source));
    assertTrue(Files.exists(tempDir.resolve("AbsentT.xml")));
  }
}
