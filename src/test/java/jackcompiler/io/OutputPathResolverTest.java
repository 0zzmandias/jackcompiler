package jackcompiler.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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

  @Test
  void resolvesSyntaxXmlToItsOwnDefaultDirectory() throws IOException {
    Path source = tempDir.resolve("Main.jack");
    Path actual = resolver.resolve(source, Optional.empty(), OutputKind.SYNTAX_XML);
    assertEquals(
        Path.of("target", "generated-parser", "MainP.xml").toAbsolutePath().normalize(), actual);
  }

  @Test
  void resolvesCustomSyntaxXmlOutputDirectory() throws IOException {
    Path source = tempDir.resolve("Main.jack");
    Path directory = tempDir.resolve("output");
    assertEquals(
        directory.resolve("MainP.xml"),
        resolver.resolve(source, Optional.of(directory), OutputKind.SYNTAX_XML));
  }

  @Test
  void normalizesCustomRelativeSyntaxXmlDirectory() throws IOException {
    Path source = tempDir.resolve("Main.jack");
    Path relative = Path.of("outputs", "subfolder", "..");
    assertEquals(
        Path.of("outputs", "MainP.xml").toAbsolutePath().normalize(),
        resolver.resolve(source, Optional.of(relative), OutputKind.SYNTAX_XML));
  }

  @Test
  void preservesUnicodeAndDotsInSyntaxXmlFileName() throws IOException {
    Path source = tempDir.resolve("Olá.test.jack");
    assertEquals(
        tempDir.resolve("out").resolve("Olá.testP.xml"),
        resolver.resolve(source, Optional.of(tempDir.resolve("out")), OutputKind.SYNTAX_XML));
  }

  @Test
  void doesNotCreateSyntaxXmlOutputDirectoryOrFile() throws IOException {
    Path directory = tempDir.resolve("not-created");
    Path source = tempDir.resolve("Main.jack");
    Path destination = resolver.resolve(source, Optional.of(directory), OutputKind.SYNTAX_XML);
    assertFalse(Files.exists(directory));
    assertFalse(Files.exists(destination));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Main.txt", "Main.JACK", "Main.jack.bak", "Main", ".jack"})
  void rejectsInvalidSyntaxXmlSourceNames(String name) {
    Path source = tempDir.resolve(name);
    assertThrows(
        IllegalArgumentException.class,
        () -> resolver.resolve(source, Optional.empty(), OutputKind.SYNTAX_XML));
  }

  @Test
  void rejectsHardLinkAliasForSyntaxXmlDestination() throws IOException {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");
    Files.createLink(tempDir.resolve("MainP.xml"), source);
    IOException error =
        assertThrows(
            IOException.class,
            () -> resolver.resolve(source, Optional.of(tempDir), OutputKind.SYNTAX_XML));
    assertTrue(error.getMessage().contains("aliases the source"));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void rejectsSymbolicLinkAliasForSyntaxXmlDestination() throws IOException {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");
    Files.createSymbolicLink(tempDir.resolve("MainP.xml"), source);
    assertThrows(
        IOException.class,
        () -> resolver.resolve(source, Optional.of(tempDir), OutputKind.SYNTAX_XML));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void keepsTokenAndSyntaxXmlDestinationsDistinct() throws IOException {
    Path source = tempDir.resolve("Main.jack");
    Path directory = tempDir.resolve("out");
    Path tokens = resolver.resolve(source, Optional.of(directory), OutputKind.TOKEN_XML);
    Path syntax = resolver.resolve(source, Optional.of(directory), OutputKind.SYNTAX_XML);
    assertEquals(directory.resolve("MainT.xml"), tokens);
    assertEquals(directory.resolve("MainP.xml"), syntax);
    assertFalse(tokens.equals(syntax));
  }

  @Test
  void resolvesVmBesideStandaloneSourceByDefault() throws IOException {
    Path source = tempDir.resolve("Main.jack");
    assertEquals(
        tempDir.resolve("Main.vm"), resolver.resolve(source, Optional.empty(), OutputKind.VM));
  }

  @Test
  void resolvesStandaloneVmInsideCustomDirectory() throws IOException {
    Path source = tempDir.resolve("Main.jack");
    Path output = tempDir.resolve("generated");
    assertEquals(
        output.resolve("Main.vm"), resolver.resolve(source, Optional.of(output), OutputKind.VM));
  }

  @Test
  void resolvesStandaloneVmUsingRelativeCustomDirectory() throws IOException {
    Path source = tempDir.resolve("Main.jack");
    Path output = Path.of("target", "out", "..");
    assertEquals(
        Path.of("target", "Main.vm").toAbsolutePath().normalize(),
        resolver.resolve(source, Optional.of(output), OutputKind.VM));
  }

  @Test
  void preservesUnicodeAndDotsInVmFileNames() throws IOException {
    Path source = tempDir.resolve("Olá.test.jack");
    assertEquals(
        tempDir.resolve("Olá.test.vm"), resolver.resolve(source, Optional.empty(), OutputKind.VM));
  }

  @Test
  void resolvesDirectoryVmBesideEachNestedSource() throws IOException {
    Path source = writeNested("pkg/util/Helper.jack");
    assertEquals(
        source.resolveSibling("Helper.vm"), resolver.resolveVm(source, tempDir, Optional.empty()));
  }

  @Test
  void mirrorsRelativeDirectoriesIntoCustomVmRoot() throws IOException {
    Path source = writeNested("pkg/util/Helper.jack");
    Path output = tempDir.resolve("vm-out");
    assertEquals(
        output.resolve("pkg/util/Helper.vm"),
        resolver.resolveVm(source, tempDir, Optional.of(output)));
  }

  @Test
  void placesRootLevelSourceDirectlyUnderCustomVmRoot() throws IOException {
    Path source = tempDir.resolve("Main.jack");
    Path output = tempDir.resolve("output");
    assertEquals(
        output.resolve("Main.vm"), resolver.resolveVm(source, tempDir, Optional.of(output)));
  }

  @Test
  void differentNestedSourcesWithSameBasenameHaveDifferentTargets() throws IOException {
    Path a = writeNested("one/Main.jack");
    Path b = writeNested("two/Main.jack");
    Path output = tempDir.resolve("out");
    Map<Path, Path> batch = resolver.resolveVmBatch(tempDir, List.of(a, b), Optional.of(output));
    assertEquals(output.resolve("one/Main.vm"), batch.get(a));
    assertEquals(output.resolve("two/Main.vm"), batch.get(b));
  }

  @Test
  void batchPreservesSourceOrdering() throws IOException {
    Path a = writeNested("A.jack");
    Path b = writeNested("B.jack");
    Map<Path, Path> batch = resolver.resolveVmBatch(tempDir, List.of(b, a), Optional.empty());
    assertEquals(List.of(b, a), List.copyOf(batch.keySet()));
  }

  @Test
  void batchDestinationsAreImmutable() throws IOException {
    Path a = writeNested("A.jack");
    Map<Path, Path> batch = resolver.resolveVmBatch(tempDir, List.of(a), Optional.empty());
    assertThrows(UnsupportedOperationException.class, () -> batch.put(a, a));
  }

  @Test
  void preservesDistinctVmAndXmlSuffixes() throws IOException {
    Path source = tempDir.resolve("Main.jack");
    Path output = tempDir;
    assertEquals(
        tempDir.resolve("Main.vm"), resolver.resolve(source, Optional.of(output), OutputKind.VM));
    assertEquals(
        tempDir.resolve("MainT.xml"),
        resolver.resolve(source, Optional.of(output), OutputKind.TOKEN_XML));
    assertEquals(
        tempDir.resolve("MainP.xml"),
        resolver.resolve(source, Optional.of(output), OutputKind.SYNTAX_XML));
  }

  @Test
  void noOutputDirectoryUsesReplacePolicy() {
    assertEquals(WritePolicy.REPLACE_EXISTING, resolver.writePolicy(Optional.empty()));
  }

  @Test
  void customOutputDirectoryUsesCreateNewPolicy() {
    assertEquals(WritePolicy.CREATE_NEW, resolver.writePolicy(Optional.of(tempDir.resolve("out"))));
  }

  @Test
  void resolvingVmNeverCreatesFilesOrDirectories() throws IOException {
    Path source = tempDir.resolve("Main.jack");
    Path output = tempDir.resolve("output/subfolder");
    Path destination = resolver.resolve(source, Optional.of(output), OutputKind.VM);
    assertFalse(Files.exists(destination));
    assertFalse(Files.exists(output));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Main.txt", "Main.JACK", "Main.jack.bak", "Main", ".jack"})
  void rejectsInvalidVmSourceNames(String name) {
    assertThrows(
        IllegalArgumentException.class,
        () -> resolver.resolve(tempDir.resolve(name), Optional.empty(), OutputKind.VM));
  }

  @Test
  void rejectsNullVmArguments() {
    Path source = tempDir.resolve("Main.jack");
    assertThrows(
        NullPointerException.class, () -> resolver.resolveVm(null, source, Optional.empty()));
    assertThrows(
        NullPointerException.class, () -> resolver.resolveVm(source, null, Optional.empty()));
    assertThrows(NullPointerException.class, () -> resolver.resolveVm(source, source, null));
    assertThrows(NullPointerException.class, () -> resolver.writePolicy(null));
  }

  @Test
  void rejectsNullVmBatchArguments() {
    Path source = tempDir.resolve("Main.jack");
    assertThrows(
        NullPointerException.class,
        () -> resolver.resolveVmBatch(null, List.of(source), Optional.empty()));
    assertThrows(
        NullPointerException.class, () -> resolver.resolveVmBatch(source, null, Optional.empty()));
    assertThrows(
        NullPointerException.class, () -> resolver.resolveVmBatch(source, List.of(source), null));
  }

  @Test
  void rejectsEmptyVmSourceBatch() {
    assertThrows(
        IOException.class, () -> resolver.resolveVmBatch(tempDir, List.of(), Optional.empty()));
  }

  @Test
  void rejectsVmSourceOutsideRequestedDirectory() throws IOException {
    Path source = writeNested("outside/Main.jack");
    Path input = Files.createDirectory(tempDir.resolve("input"));
    assertThrows(
        IllegalArgumentException.class,
        () -> resolver.resolveVm(source, input, Optional.of(tempDir.resolve("out"))));
  }

  @Test
  void rejectsVmSourceThatDiffersFromRequestedFile() throws IOException {
    Path first = writeNested("A.jack");
    Path second = writeNested("B.jack");
    assertThrows(
        IllegalArgumentException.class, () -> resolver.resolveVm(second, first, Optional.empty()));
  }

  @Test
  void rejectsSymbolicInputDirectoryForVmResolution() throws IOException {
    Path input = Files.createDirectory(tempDir.resolve("input"));
    Path source = Files.writeString(input.resolve("Main.jack"), "class Main {}");
    Path alias = Files.createSymbolicLink(tempDir.resolve("linked-input"), input);
    assertThrows(
        IllegalArgumentException.class,
        () -> resolver.resolveVm(source, alias, Optional.of(tempDir.resolve("out"))));
  }

  @Test
  void rejectsDuplicateVmSourcesInBatch() throws IOException {
    Path source = writeNested("Main.jack");
    IOException error =
        assertThrows(
            IOException.class,
            () -> resolver.resolveVmBatch(tempDir, List.of(source, source), Optional.empty()));
    assertTrue(error.getMessage().contains("Duplicate VM output"));
  }

  @Test
  void rejectsVmDestinationHardLinkedToItsOwnSource() throws IOException {
    Path source = writeNested("Main.jack");
    Files.createLink(tempDir.resolve("Main.vm"), source);
    IOException error =
        assertThrows(
            IOException.class, () -> resolver.resolve(source, Optional.empty(), OutputKind.VM));
    assertTrue(error.getMessage().contains("aliases a Jack source"));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void rejectsVmDestinationAliasingAnotherSourceInBatch() throws IOException {
    Path a = writeNested("A.jack");
    Path b = writeNested("B.jack");
    Files.createLink(tempDir.resolve("A.vm"), b);
    IOException error =
        assertThrows(
            IOException.class,
            () -> resolver.resolveVmBatch(tempDir, List.of(a, b), Optional.empty()));
    assertTrue(error.getMessage().contains("aliases a Jack source"));
    assertEquals("class Main {}", Files.readString(b));
  }

  @Test
  void rejectsSymbolicVmDestination() throws IOException {
    Path source = writeNested("Main.jack");
    Files.createSymbolicLink(tempDir.resolve("Main.vm"), source);
    IOException error =
        assertThrows(
            IOException.class, () -> resolver.resolve(source, Optional.empty(), OutputKind.VM));
    assertTrue(error.getMessage().contains("Symbolic-link output destination"));
  }

  @Test
  void rejectsDanglingSymbolicVmDestination() throws IOException {
    Path source = writeNested("Main.jack");
    Files.createSymbolicLink(tempDir.resolve("Main.vm"), tempDir.resolve("missing"));
    assertThrows(
        IOException.class, () -> resolver.resolve(source, Optional.empty(), OutputKind.VM));
  }

  @Test
  void rejectsSymbolicVmOutputParent() throws IOException {
    Path source = writeNested("Main.jack");
    Path output = Files.createDirectory(tempDir.resolve("real-output"));
    Path alias = Files.createSymbolicLink(tempDir.resolve("linked-output"), output);
    IOException error =
        assertThrows(
            IOException.class, () -> resolver.resolve(source, Optional.of(alias), OutputKind.VM));
    assertTrue(error.getMessage().contains("Symbolic-link output directory"));
  }

  @Test
  void rejectsRegularFileAsVmOutputParent() throws IOException {
    Path source = writeNested("Main.jack");
    Path output = Files.writeString(tempDir.resolve("not-a-directory"), "content");
    IOException error =
        assertThrows(
            IOException.class, () -> resolver.resolve(source, Optional.of(output), OutputKind.VM));
    assertTrue(error.getMessage().contains("Output parent is not a directory"));
  }

  @Test
  void rejectsDirectoryOccupyingVmOutputFile() throws IOException {
    Path source = writeNested("Main.jack");
    Files.createDirectory(tempDir.resolve("Main.vm"));
    IOException error =
        assertThrows(
            IOException.class, () -> resolver.resolve(source, Optional.empty(), OutputKind.VM));
    assertTrue(error.getMessage().contains("not a regular file"));
  }

  @Test
  void rejectsVmOutputInsideFixtureDirectory() throws IOException {
    Path source = writeNested("Main.jack");
    Path output = tempDir.resolve("src/test/resources/fixtures");
    IOException error =
        assertThrows(
            IOException.class, () -> resolver.resolve(source, Optional.of(output), OutputKind.VM));
    assertTrue(error.getMessage().contains("source or reference directories"));
    assertFalse(Files.exists(output));
  }

  @Test
  void rejectsVmOutputInsideProductionSourceDirectory() throws IOException {
    Path source = writeNested("Main.jack");
    Path output = tempDir.resolve("src/main/java/jackcompiler");
    assertThrows(
        IOException.class, () -> resolver.resolve(source, Optional.of(output), OutputKind.VM));
    assertFalse(Files.exists(output));
  }

  @Test
  void permitsReplacingExistingRegularVmInDefaultDirectory() throws IOException {
    Path source = writeNested("Main.jack");
    Path destination = Files.writeString(tempDir.resolve("Main.vm"), "old output");
    assertEquals(destination, resolver.resolve(source, Optional.empty(), OutputKind.VM));
    assertEquals("old output", Files.readString(destination));
  }

  @Test
  void rejectsExistingRegularVmInCustomDirectory() throws IOException {
    Path source = writeNested("Main.jack");
    Path output = Files.createDirectory(tempDir.resolve("output"));
    Path destination = Files.writeString(output.resolve("Main.vm"), "old output");
    assertThrows(
        FileAlreadyExistsException.class,
        () -> resolver.resolve(source, Optional.of(output), OutputKind.VM));
    assertEquals("old output", Files.readString(destination));
  }

  @Test
  void preservesRelativeSourcePathAcrossNormalizedInput() throws IOException {
    Path source = writeNested("lib/math/Adder.jack");
    Path input = tempDir.resolve("lib/../");
    Path out = tempDir.resolve("out");
    assertEquals(
        out.resolve("lib/math/Adder.vm"), resolver.resolveVm(source, input, Optional.of(out)));
  }

  @Test
  void canPlanBatchWithNoFileSystemWrites() throws IOException {
    Path first = writeNested("src/A.jack");
    Path second = writeNested("src/deep/B.jack");
    Path output = tempDir.resolve("output");
    Map<Path, Path> planned =
        resolver.resolveVmBatch(tempDir, List.of(first, second), Optional.of(output));
    assertEquals(2, planned.size());
    assertFalse(Files.exists(output));
  }

  private Path writeNested(String path) throws IOException {
    Path source = tempDir.resolve(path);
    Files.createDirectories(source.getParent());
    return Files.writeString(source, "class Main {}");
  }
}
