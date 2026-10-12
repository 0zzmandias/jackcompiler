package jackcompiler.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.JackTokenizer;
import jackcompiler.token.TokenType;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class JackSourceReaderTest {

  @TempDir Path tempDir;

  private final JackSourceReader reader = new JackSourceReader();

  @Test
  void readsUtf8SourceText() throws IOException {
    String original = "class Main { /* Olá 😀 */ }";
    Path file = write("Main.jack", original);

    assertEquals(original, reader.read(file));
  }

  @Test
  void preservesOriginalLineEndings() throws IOException {
    String original = "class Main {\r\n\treturn;\r\n}\n";
    Path file = write("Main.jack", original);

    String loaded = reader.read(file);

    assertEquals(original, loaded);
    assertTrue(loaded.contains("\r\n"));
    assertTrue(loaded.endsWith("\n"));
  }

  @Test
  void readsEmptyJackFile() throws IOException {
    Path file = write("Empty.jack", "");

    assertEquals("", reader.read(file));
  }

  @Test
  void integratesFileReadingWithJackTokenizer() throws IOException {
    Path file = write("Main.jack", "class Main {}");

    Path selected = new JackFileResolver().resolve(file).get(0);
    String source = reader.read(selected);
    var result = new JackTokenizer().tokenize(source);

    assertEquals(
        List.of("class", "Main", "{", "}"),
        result.tokens().stream().map(token -> token.value()).toList());

    assertEquals(TokenType.KEYWORD, result.tokens().get(0).type());
    assertEquals(TokenType.IDENTIFIER, result.tokens().get(1).type());
  }

  @Test
  void rejectsReadingMissingFile() {
    Path missing = tempDir.resolve("Missing.jack");

    assertThrows(NoSuchFileException.class, () -> reader.read(missing));
  }

  @ParameterizedTest
  @ValueSource(strings = {"notes.txt", "Main.JACK", "Main.jack.bak"})
  void rejectsReadingNonJackFile(String name) throws IOException {
    Path file = write(name, "content");

    assertThrows(IllegalArgumentException.class, () -> reader.read(file));
  }

  @Test
  void rejectsNullReadInput() {
    assertThrows(NullPointerException.class, () -> reader.read(null));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("malformedUtf8Sequences")
  void rejectsMalformedUtf8WithoutCharacterReplacement(String description, byte[] bytes)
      throws IOException {
    Path file = tempDir.resolve("Malformed.jack");
    Files.write(file, bytes);

    assertThrows(MalformedInputException.class, () -> reader.read(file), description);
    assertArrayEquals(bytes, Files.readAllBytes(file));
  }

  @Test
  void preservesLegitimatelyEncodedReplacementCharacter() throws IOException {
    String original = "class Main { /* \uFFFD */ }";
    Path file = write("Main.jack", original);

    assertEquals(original, reader.read(file));
  }

  @Test
  void doesNotModifySourceBytes() throws IOException {
    Path file = write("Main.jack", "class Main {\r\n  // Olá 😀\r\n}\n");
    byte[] before = Files.readAllBytes(file);

    reader.read(file);

    assertArrayEquals(before, Files.readAllBytes(file));
  }

  @Test
  void rejectsReadingDirectoryAsSourceFile() throws IOException {
    Path directory = Files.createDirectory(tempDir.resolve("Examples.jack"));

    assertThrows(IOException.class, () -> reader.read(directory));
  }

  @Test
  void supportsIndependentRepeatedReads() throws IOException {
    Path file = write("Main.jack", "class First {}");

    assertEquals("class First {}", reader.read(file));

    Files.writeString(file, "class Second {}", StandardCharsets.UTF_8);

    assertEquals("class Second {}", reader.read(file));
  }

  @Test
  void preservesUtf8ByteOrderMarkWithoutSilentlyRemovingIt() throws IOException {
    String original = "\uFEFFclass Main {}";
    Path file = write("Main.jack", original);

    assertEquals(original, reader.read(file));
  }

  private static Stream<Arguments> malformedUtf8Sequences() {
    return Stream.of(
        Arguments.of("Unexpected continuation byte", new byte[] {(byte) 0x80}),
        Arguments.of("Incomplete two-byte sequence", new byte[] {(byte) 0xC3}),
        Arguments.of("Invalid continuation byte", new byte[] {(byte) 0xC3, (byte) 0x28}));
  }

  private Path write(String name, String content) throws IOException {
    return Files.writeString(tempDir.resolve(name), content, StandardCharsets.UTF_8);
  }
}
