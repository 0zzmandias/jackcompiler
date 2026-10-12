package jackcompiler.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JackFileResolverTest {

  @TempDir Path tempDir;

  private final JackFileResolver resolver = new JackFileResolver();

  @Test
  void resolvesIndividualJackFile() throws IOException {
    Path file = write("Main.jack", "class Main {}");

    assertEquals(List.of(file), resolver.resolve(file));
  }

  @Test
  void normalizesRelativeInputPath() throws IOException {
    Path file = write("Main.jack", "class Main {}");
    Path relative = Path.of("").toAbsolutePath().relativize(file);

    assertEquals(List.of(file), resolver.resolve(relative));
  }

  @Test
  void sortsDirectorySourcesByFileName() throws IOException {
    Path zebra = write("Zebra.jack", "class Zebra {}");
    Path main = write("Main.jack", "class Main {}");
    Path alpha = write("Alpha.jack", "class Alpha {}");

    assertEquals(List.of(alpha, main, zebra), resolver.resolve(tempDir));
  }

  @Test
  void ignoresFilesWithOtherExtensions() throws IOException {
    Path main = write("Main.jack", "class Main {}");

    write("README.md", "documentation");
    write("Main.JACK", "uppercase extension");
    write("Main.jack.bak", "backup");

    assertEquals(List.of(main), resolver.resolve(tempDir));
  }

  @Test
  void doesNotSearchSubdirectoriesRecursively() throws IOException {
    Path main = write("Main.jack", "class Main {}");
    Path nestedDirectory = Files.createDirectory(tempDir.resolve("nested"));

    Files.writeString(nestedDirectory.resolve("Other.jack"), "class Other {}");

    assertEquals(List.of(main), resolver.resolve(tempDir));
  }

  @Test
  void acceptsDirectoryWhoseNameEndsWithJackExtension() throws IOException {
    Path directory = Files.createDirectory(tempDir.resolve("Examples.jack"));
    Path source = Files.writeString(directory.resolve("Main.jack"), "class Main {}");

    assertEquals(List.of(source), resolver.resolve(directory));
  }

  @Test
  void returnsUnmodifiableSourceList() throws IOException {
    write("Main.jack", "class Main {}");

    List<Path> sources = resolver.resolve(tempDir);

    assertThrows(
        UnsupportedOperationException.class, () -> sources.add(tempDir.resolve("Other.jack")));
  }

  @Test
  void rejectsMissingInputPath() {
    Path missing = tempDir.resolve("Missing.jack");

    assertThrows(NoSuchFileException.class, () -> resolver.resolve(missing));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Main.txt", "Main.JACK", "Main.jack.bak"})
  void rejectsIndividualFilesWithoutExactJackExtension(String fileName) throws IOException {
    Path file = write(fileName, "content");

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(file));

    assertTrue(error.getMessage().contains(".jack"));
  }

  @Test
  void rejectsEmptyDirectory() {
    IOException error = assertThrows(IOException.class, () -> resolver.resolve(tempDir));

    assertTrue(error.getMessage().contains("No .jack files"));
  }

  @Test
  void rejectsDirectoryContainingNoJackFiles() throws IOException {
    write("README.md", "documentation");
    write("Main.JACK", "class Main {}");

    IOException error = assertThrows(IOException.class, () -> resolver.resolve(tempDir));

    assertTrue(error.getMessage().contains("No .jack files"));
  }

  @Test
  void acceptsIndependentRepeatedResolutions() throws IOException {
    Path first = write("A.jack", "class A {}");

    assertEquals(List.of(first), resolver.resolve(tempDir));

    Path second = write("B.jack", "class B {}");

    assertEquals(List.of(first, second), resolver.resolve(tempDir));
  }

  @Test
  void rejectsNullResolveInput() {
    assertThrows(NullPointerException.class, () -> resolver.resolve(null));
  }

  @Test
  void returnsOnlyRegularFilesFromDirectory() throws IOException {
    Path main = write("Main.jack", "class Main {}");
    Files.createDirectory(tempDir.resolve("Nested.jack"));

    List<Path> sources = resolver.resolve(tempDir);

    assertEquals(List.of(main), sources);
    assertFalse(sources.contains(tempDir.resolve("Nested.jack")));
  }

  private Path write(String name, String content) throws IOException {
    return Files.writeString(tempDir.resolve(name), content, StandardCharsets.UTF_8);
  }
}
