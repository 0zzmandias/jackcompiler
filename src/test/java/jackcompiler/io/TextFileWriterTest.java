package jackcompiler.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TextFileWriterTest {

  @TempDir Path tempDir;

  private final TextFileWriter writer = new TextFileWriter();

  @Test
  void createsMissingParentsAndWritesExactUtf8Bytes() throws IOException {
    Path destination = tempDir.resolve("output/subfolder/MainT.xml");

    String content = "<tokens>\n" + "<stringConstant> Olá 😀 </stringConstant>\n" + "</tokens>\n";

    Path written = writer.write(destination, content, WritePolicy.CREATE_NEW);

    assertEquals(destination, written);
    assertEquals(content, Files.readString(destination, StandardCharsets.UTF_8));

    assertArrayEquals(content.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(destination));

    assertNoTemporaryFiles(destination.getParent());
  }

  @Test
  void createsNewOutputWithoutReplacingExistingFile() throws IOException {
    Path destination = tempDir.resolve("MainT.xml");

    Files.writeString(destination, "original");

    assertThrows(
        FileAlreadyExistsException.class,
        () -> writer.write(destination, "changed", WritePolicy.CREATE_NEW));

    assertEquals("original", Files.readString(destination));
    assertNoTemporaryFiles(tempDir);
  }

  @Test
  void replacesExistingRegularFileWithoutLeavingTemporaryFiles() throws IOException {
    Path destination = Files.writeString(tempDir.resolve("MainT.xml"), "original");

    assertEquals(destination, writer.write(destination, "updated", WritePolicy.REPLACE_EXISTING));

    assertEquals("updated", Files.readString(destination));
    assertNoTemporaryFiles(tempDir);
  }

  @Test
  void replaceExistingAlsoCreatesOutputWhenMissing() throws IOException {
    Path destination = tempDir.resolve("new").resolve("MainT.xml");

    writer.write(destination, "new content", WritePolicy.REPLACE_EXISTING);

    assertEquals("new content", Files.readString(destination));
    assertNoTemporaryFiles(destination.getParent());
  }

  @ParameterizedTest
  @EnumSource(WritePolicy.class)
  void rejectsOutputDestinationThatIsADirectory(WritePolicy policy) throws IOException {
    Path destination = Files.createDirectory(tempDir.resolve("MainT.xml"));

    assertThrows(IOException.class, () -> writer.write(destination, "content", policy));

    assertTrue(Files.isDirectory(destination));
    assertNoTemporaryFiles(tempDir);
  }

  @ParameterizedTest
  @EnumSource(WritePolicy.class)
  void rejectsSymbolicLinkDestinationWithoutTouchingItsTarget(WritePolicy policy)
      throws IOException {
    Path source = Files.writeString(tempDir.resolve("official.xml"), "original");

    Path destination = Files.createSymbolicLink(tempDir.resolve("MainT.xml"), source);

    assertThrows(IOException.class, () -> writer.write(destination, "changed", policy));

    assertEquals("original", Files.readString(source));
    assertTrue(Files.isSymbolicLink(destination));
    assertNoTemporaryFiles(tempDir);
  }

  @Test
  void rejectsDanglingSymbolicLinkAsDestination() throws IOException {
    Path destination =
        Files.createSymbolicLink(tempDir.resolve("MainT.xml"), tempDir.resolve("not-found.xml"));

    assertThrows(
        IOException.class,
        () -> writer.write(destination, "changed", WritePolicy.REPLACE_EXISTING));

    assertTrue(Files.exists(destination, LinkOption.NOFOLLOW_LINKS));
    assertNoTemporaryFiles(tempDir);
  }

  @Test
  void rejectsSymbolicLinkInParentDirectory() throws IOException {
    Path real = Files.createDirectory(tempDir.resolve("real"));

    Path linked = Files.createSymbolicLink(tempDir.resolve("linked"), real);

    assertThrows(
        IOException.class,
        () -> writer.write(linked.resolve("MainT.xml"), "unsafe", WritePolicy.CREATE_NEW));

    assertFalse(Files.exists(real.resolve("MainT.xml")));
  }

  @Test
  void rejectsSourceFileDestinationWithoutModifyingIt() throws IOException {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");

    assertThrows(
        IOException.class, () -> writer.write(source, "unsafe", WritePolicy.REPLACE_EXISTING));

    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void rejectsFixtureDirectoryWithoutCreatingFiles() throws IOException {
    Path protectedDestination = tempDir.resolve("src/test/resources/nand2tetris/MainT.xml");

    assertThrows(
        IOException.class,
        () -> writer.write(protectedDestination, "unsafe", WritePolicy.CREATE_NEW));

    assertFalse(Files.exists(protectedDestination));
  }

  @Test
  void rejectsSourceCodeDirectoryWithoutCreatingFiles() throws IOException {
    Path protectedDestination = tempDir.resolve("src/main/java/MainT.xml");

    assertThrows(
        IOException.class,
        () -> writer.write(protectedDestination, "unsafe", WritePolicy.CREATE_NEW));

    assertFalse(Files.exists(protectedDestination));
  }

  @Test
  void avoidsModifyingAJackFileThroughExistingHardLink() throws IOException {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");

    Path destination = tempDir.resolve("MainT.xml");

    Files.createLink(destination, source);

    writer.write(destination, "<tokens>\n</tokens>\n", WritePolicy.REPLACE_EXISTING);

    assertEquals("class Main {}", Files.readString(source));

    assertEquals("<tokens>\n</tokens>\n", Files.readString(destination));

    assertNoTemporaryFiles(tempDir);
  }

  @Test
  void normalizesRelativeDestination() throws IOException {
    Path relative = Path.of("target", "generated-tokens", "..", "generated-tokens", "MainT.xml");

    Path destination = tempDir.resolve(relative);

    Path actual = writer.write(destination, "xml", WritePolicy.CREATE_NEW);

    assertEquals(tempDir.resolve("target/generated-tokens/MainT.xml"), actual);

    assertEquals("xml", Files.readString(actual));
  }

  @Test
  void createsEmptyOutputFileForEmptyContent() throws IOException {
    Path destination = tempDir.resolve("EmptyT.xml");

    writer.write(destination, "", WritePolicy.CREATE_NEW);

    assertEquals(0L, Files.size(destination));
    assertNoTemporaryFiles(tempDir);
  }

  @Test
  void rejectsNullArgumentsWithoutCreatingOutput() {
    Path destination = tempDir.resolve("MainT.xml");

    assertThrows(
        NullPointerException.class, () -> writer.write(null, "data", WritePolicy.CREATE_NEW));

    assertThrows(
        NullPointerException.class, () -> writer.write(destination, null, WritePolicy.CREATE_NEW));

    assertThrows(NullPointerException.class, () -> writer.write(destination, "data", null));

    assertFalse(Files.exists(destination));
  }

  @Test
  void rejectsMalformedTextWithoutPublishingPartialOutput() throws IOException {
    Path destination = tempDir.resolve("MainT.xml");

    assertThrows(
        IOException.class,
        () -> writer.write(destination, "before\uD800after", WritePolicy.CREATE_NEW));

    assertFalse(Files.exists(destination));
    assertNoTemporaryFiles(tempDir);
  }

  @Test
  void worksWithResolverForDefaultOutputPath() throws IOException {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");

    Path destination =
        new OutputPathResolver()
            .resolve(source, Optional.of(tempDir.resolve("generated")), OutputKind.TOKEN_XML);

    writer.write(destination, "<tokens>\n</tokens>\n", WritePolicy.CREATE_NEW);

    assertEquals("<tokens>\n</tokens>\n", Files.readString(destination));

    assertEquals("class Main {}", Files.readString(source));
  }

  private static void assertNoTemporaryFiles(Path directory) throws IOException {
    try (var entries = Files.list(directory)) {
      assertEquals(
          0L,
          entries
              .filter(path -> path.getFileName().toString().startsWith(".jackcompiler-"))
              .count());
    }
  }
}
