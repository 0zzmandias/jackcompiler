package jackcompiler.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OutputPathResolverTest {

  @TempDir Path tempDir;

  private final OutputPathResolver resolver = new OutputPathResolver();

  @Test
  void resolvesDefaultDirectoryRelativeToWorkingDirectory() throws IOException {
    Path source = tempDir.resolve("Main.jack");

    Path actual = resolver.resolve(source, Optional.empty(), OutputKind.TOKEN_XML);

    assertEquals(
        Path.of("target", "generated-tokens", "MainT.xml").toAbsolutePath().normalize(), actual);
  }

  @Test
  void resolvesCustomOutputDirectory() throws IOException {
    Path source = tempDir.resolve("Main.jack");
    Path directory = tempDir.resolve("output");

    assertEquals(
        directory.resolve("MainT.xml"),
        resolver.resolve(source, Optional.of(directory), OutputKind.TOKEN_XML));
  }

  @Test
  void normalizesCustomRelativeOutputDirectory() throws IOException {
    Path source = tempDir.resolve("Main.jack");
    Path relative = Path.of("outputs", "subfolder", "..");

    assertEquals(
        Path.of("outputs", "MainT.xml").toAbsolutePath().normalize(),
        resolver.resolve(source, Optional.of(relative), OutputKind.TOKEN_XML));
  }

  @Test
  void preservesUnicodeAndDotsInFileName() throws IOException {
    Path source = tempDir.resolve("Olá.test.jack");

    assertEquals(
        tempDir.resolve("out").resolve("Olá.testT.xml"),
        resolver.resolve(source, Optional.of(tempDir.resolve("out")), OutputKind.TOKEN_XML));
  }

  @Test
  void doesNotCreateDirectoriesOrOutputFiles() throws IOException {
    Path directory = tempDir.resolve("not-created");
    Path source = tempDir.resolve("Main.jack");

    Path destination = resolver.resolve(source, Optional.of(directory), OutputKind.TOKEN_XML);

    assertFalse(Files.exists(directory));
    assertFalse(Files.exists(destination));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Main.txt", "Main.JACK", "Main.jack.bak", "Main", ".jack"})
  void rejectsInvalidSourceNames(String name) {
    Path source = tempDir.resolve(name);

    assertThrows(
        IllegalArgumentException.class,
        () -> resolver.resolve(source, Optional.empty(), OutputKind.TOKEN_XML));
  }

  @Test
  void rejectsNullSource() {
    assertThrows(
        NullPointerException.class,
        () -> resolver.resolve(null, Optional.empty(), OutputKind.TOKEN_XML));
  }

  @Test
  void rejectsNullOutputDirectoryOptional() {
    assertThrows(
        NullPointerException.class,
        () -> resolver.resolve(tempDir.resolve("Main.jack"), null, OutputKind.TOKEN_XML));
  }

  @Test
  void rejectsNullOutputKind() {
    assertThrows(
        NullPointerException.class,
        () -> resolver.resolve(tempDir.resolve("Main.jack"), Optional.empty(), null));
  }

  @Test
  void rejectsHardLinkAliasToExistingSource() throws IOException {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");

    Files.createLink(tempDir.resolve("MainT.xml"), source);

    IOException error =
        assertThrows(
            IOException.class,
            () -> resolver.resolve(source, Optional.of(tempDir), OutputKind.TOKEN_XML));

    assertTrue(error.getMessage().contains("aliases the source"));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void rejectsSymbolicLinkAliasToExistingSource() throws IOException {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");

    Files.createSymbolicLink(tempDir.resolve("MainT.xml"), source);

    assertThrows(
        IOException.class,
        () -> resolver.resolve(source, Optional.of(tempDir), OutputKind.TOKEN_XML));

    assertEquals("class Main {}", Files.readString(source));
  }
}
