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

  @Test
  void recursiveDiscoveryAcceptsIndividualJackFile() throws IOException {
    Path file = write("Main.jack", "class Main {}");

    assertEquals(List.of(file), resolver.resolveRecursively(file));
  }

  @Test
  void recursiveDiscoveryNormalizesRelativeFileInput() throws IOException {
    Path file = write("Main.jack", "class Main {}");
    Path relative = Path.of("").toAbsolutePath().relativize(file);

    assertEquals(List.of(file), resolver.resolveRecursively(relative));
  }

  @Test
  void recursiveDiscoveryIncludesRootAndDeeplyNestedFiles() throws IOException {
    Path root = write("Main.jack", "class Main {}");
    Path nested = writeNested("src/Other.jack", "class Other {}");
    Path deep = writeNested("src/helpers/Util.jack", "class Util {}");

    assertEquals(List.of(root, nested, deep), resolver.resolveRecursively(tempDir));
  }

  @Test
  void recursiveDiscoveryFindsSourcesWhenRootHasNone() throws IOException {
    Path deep = writeNested("a/b/c/Only.jack", "class Only {}");

    assertEquals(List.of(deep), resolver.resolveRecursively(tempDir));
  }

  @Test
  void recursiveDiscoverySortsByRelativePathRatherThanFileName() throws IOException {
    Path aZ = writeNested("A/Z.jack", "class Z {}");
    Path bA = writeNested("B/A.jack", "class A {}");

    assertEquals(List.of(aZ, bA), resolver.resolveRecursively(tempDir));
  }

  @Test
  void recursiveDiscoveryKeepsSameFileNamesInDifferentDirectories() throws IOException {
    Path first = writeNested("one/Main.jack", "class Main {}");
    Path second = writeNested("two/Main.jack", "class Main {}");

    assertEquals(List.of(first, second), resolver.resolveRecursively(tempDir));
  }

  @Test
  void recursiveDiscoveryRejectsOtherExtensionsAtEveryDepth() throws IOException {
    Path expected = writeNested("src/Main.jack", "class Main {}");
    write("README.md", "readme");
    writeNested("src/Main.JACK", "upper");
    writeNested("src/deep/Other.txt", "text");
    writeNested("src/deep/Other.jack.bak", "backup");

    assertEquals(List.of(expected), resolver.resolveRecursively(tempDir));
  }

  @Test
  void recursiveDiscoveryAcceptsDirectoriesNamedWithJackExtension() throws IOException {
    Path source = writeNested("folder.jack/Other.jack", "class Other {}");

    assertEquals(List.of(source), resolver.resolveRecursively(tempDir));
  }

  @Test
  void recursiveDiscoveryDoesNotFollowDirectorySymbolicLinks() throws IOException {
    Path root = Files.createDirectory(tempDir.resolve("input"));
    Path external = Files.createDirectory(tempDir.resolve("external"));
    Path main = Files.writeString(root.resolve("Main.jack"), "class Main {}");
    Files.writeString(external.resolve("Hidden.jack"), "class Hidden {}");
    Files.createSymbolicLink(root.resolve("shortcut"), external);

    assertEquals(List.of(main), resolver.resolveRecursively(root));
  }

  @Test
  void recursiveDiscoveryRejectsSymbolicDirectoryAsInput() throws IOException {
    Path real = Files.createDirectory(tempDir.resolve("actual"));
    Files.writeString(real.resolve("Main.jack"), "class Main {}");
    Path link = Files.createSymbolicLink(tempDir.resolve("alias"), real);

    IOException error = assertThrows(IOException.class, () -> resolver.resolveRecursively(link));
    assertTrue(error.getMessage().contains("symbolic directory"));
  }

  @Test
  void recursiveDiscoveryRejectsDirectoryContainingOnlyLinkedJackSources() throws IOException {
    Path root = Files.createDirectory(tempDir.resolve("input"));
    Path other = Files.createDirectory(tempDir.resolve("other"));
    Files.writeString(other.resolve("Hidden.jack"), "class Hidden {}");
    Files.createSymbolicLink(root.resolve("shortcut"), other);

    IOException error = assertThrows(IOException.class, () -> resolver.resolveRecursively(root));
    assertTrue(error.getMessage().contains("No .jack files"));
  }

  @Test
  void recursiveDiscoveryDoesNotFollowSymbolicDirectoryCycles() throws IOException {
    Path source = writeNested("project/Main.jack", "class Main {}");
    Path root = tempDir.resolve("project");
    Files.createSymbolicLink(root.resolve("loop"), root);

    assertEquals(List.of(source), resolver.resolveRecursively(root));
  }

  @Test
  void recursiveDiscoveryAcceptsRegularFileSymbolicLinks() throws IOException {
    Path target = write("source.txt", "class Main {}");
    Path link = Files.createSymbolicLink(tempDir.resolve("Alias.jack"), target);

    assertEquals(List.of(link), resolver.resolveRecursively(tempDir));
  }

  @Test
  void recursiveDiscoveryRejectsEmptyDirectory() {
    IOException error = assertThrows(IOException.class, () -> resolver.resolveRecursively(tempDir));

    assertTrue(error.getMessage().contains("No .jack files"));
  }

  @Test
  void recursiveDiscoveryRejectsDirectoriesWithNoEligibleSources() throws IOException {
    Files.createDirectories(tempDir.resolve("empty/subdir"));
    writeNested("other/Main.JACK", "class Main {}");

    IOException error = assertThrows(IOException.class, () -> resolver.resolveRecursively(tempDir));
    assertTrue(error.getMessage().contains("No .jack files"));
  }

  @Test
  void recursiveDiscoveryRejectsMissingInput() {
    assertThrows(
        NoSuchFileException.class, () -> resolver.resolveRecursively(tempDir.resolve("missing")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Main.txt", "Main.JACK", "Main.jack.bak"})
  void recursiveDiscoveryRejectsInvalidIndividualFileExtension(String name) throws IOException {
    Path path = write(name, "content");

    assertThrows(IllegalArgumentException.class, () -> resolver.resolveRecursively(path));
  }

  @Test
  void recursiveDiscoveryRejectsNullInput() {
    assertThrows(NullPointerException.class, () -> resolver.resolveRecursively(null));
  }

  @Test
  void recursiveDiscoveryReturnsImmutableList() throws IOException {
    writeNested("folder/Main.jack", "class Main {}");

    List<Path> sources = resolver.resolveRecursively(tempDir);

    assertThrows(UnsupportedOperationException.class, () -> sources.add(tempDir));
  }

  @Test
  void recursiveDiscoveryDoesNotChangeLegacyDirectoryBehavior() throws IOException {
    Path nested = writeNested("folder/Main.jack", "class Main {}");

    assertThrows(IOException.class, () -> resolver.resolve(tempDir));
    assertEquals(List.of(nested), resolver.resolveRecursively(tempDir));
  }

  @Test
  void recursiveDiscoveryReadsCurrentContentsOnRepeatedInvocations() throws IOException {
    Path first = writeNested("A/A.jack", "class A {}");
    assertEquals(List.of(first), resolver.resolveRecursively(tempDir));

    Path second = writeNested("B/B.jack", "class B {}");
    assertEquals(List.of(first, second), resolver.resolveRecursively(tempDir));
  }

  @Test
  void recursiveDiscoveryReturnsAbsoluteNormalizedPaths() throws IOException {
    Path source = writeNested("a/b/Source.jack", "class Source {}");
    Path input = tempDir.resolve("a").resolve("..");

    List<Path> sources = resolver.resolveRecursively(input);

    assertEquals(List.of(source), sources);
    assertTrue(sources.get(0).isAbsolute());
    assertEquals(source.normalize(), sources.get(0));
  }

  private Path write(String name, String content) throws IOException {
    return Files.writeString(tempDir.resolve(name), content, StandardCharsets.UTF_8);
  }

  private Path writeNested(String name, String content) throws IOException {
    Path destination = tempDir.resolve(name);
    Files.createDirectories(destination.getParent());
    return Files.writeString(destination, content, StandardCharsets.UTF_8);
  }
}
