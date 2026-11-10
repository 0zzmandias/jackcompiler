package jackcompiler.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.compiler.CompilationException;
import jackcompiler.lexer.LexicalException;
import jackcompiler.parser.SyntaxException;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Integration tests for the recursive, fail-fast Jack-to-VM application use case. */
class CompileCommandTest {

  private static final String SIMPLE = "class Main { function void run() { return; } }";
  private static final String SIMPLE_VM = "function Main.run 0\npush constant 0\nreturn\n";

  @TempDir Path tempDir;

  private final CompileCommand command = new CompileCommand();

  @Test
  void compilesIndividualFileBesideSourceByDefault() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    List<Path> result = command.execute(source, Optional.empty());

    assertEquals(List.of(tempDir.resolve("Main.vm")), result);
    assertEquals(SIMPLE_VM, Files.readString(result.get(0)));
    assertEquals(SIMPLE, Files.readString(source));
  }

  @Test
  void compilesIndividualFileIntoCustomDirectory() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    Path outputRoot = tempDir.resolve("generated");

    assertEquals(
        List.of(outputRoot.resolve("Main.vm")), command.execute(source, Optional.of(outputRoot)));
    assertEquals(SIMPLE_VM, Files.readString(outputRoot.resolve("Main.vm")));
    assertEquals(SIMPLE, Files.readString(source));
  }

  @Test
  void normalizesRelativeInputAndCustomOutputPaths() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    Path relativeInput = Path.of("").toAbsolutePath().relativize(source);
    Path expected = tempDir.resolve("generated/Main.vm");
    Path relativeOutput =
        Path.of("").toAbsolutePath().relativize(tempDir.resolve("generated/nested/.."));

    assertEquals(List.of(expected), command.execute(relativeInput, Optional.of(relativeOutput)));
    assertEquals(SIMPLE_VM, Files.readString(expected));
  }

  @Test
  void outputListIsImmutable() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    List<Path> result = command.execute(source, Optional.empty());

    assertThrows(UnsupportedOperationException.class, () -> result.add(source));
  }

  @Test
  void compilesEmptyJackClassIntoEmptyVmFile() throws Exception {
    Path source = write("Empty.jack", "class Empty {}");

    Path destination = command.execute(source, Optional.empty()).get(0);

    assertEquals(tempDir.resolve("Empty.vm"), destination);
    assertEquals(0L, Files.size(destination));
  }

  @Test
  void compilesArithmeticAndLocalsThroughFullPipeline() throws Exception {
    Path source =
        write(
            "Main.jack",
            "class Main { function int sum(int a, int b) { "
                + "var int result; let result = a + b; return result; } }");
    String expected =
        "function Main.sum 1\npush argument 0\npush argument 1\nadd\n"
            + "pop local 0\npush local 0\nreturn\n";

    assertEquals(expected, Files.readString(command.execute(source, Optional.empty()).get(0)));
  }

  @Test
  void compilesMethodReceiverAndFieldWrite() throws Exception {
    Path source =
        write(
            "Point.jack",
            "class Point { field int x; "
                + "method void set(int value) { let x = value; return; } }");

    assertEquals(
        "function Point.set 0\npush argument 0\npop pointer 0\n"
            + "push argument 1\npop this 0\npush constant 0\nreturn\n",
        Files.readString(command.execute(source, Optional.empty()).get(0)));
  }

  @Test
  void compilesConstructorAllocationAndReturn() throws Exception {
    Path source =
        write(
            "Point.jack",
            "class Point { field int x, y; " + "constructor Point new() { return this; } }");

    assertEquals(
        "function Point.new 0\npush constant 2\ncall Memory.alloc 1\n"
            + "pop pointer 0\npush pointer 0\nreturn\n",
        Files.readString(command.execute(source, Optional.empty()).get(0)));
  }

  @Test
  void compilesOsCallWithoutProducingXml() throws Exception {
    Path source =
        write(
            "Main.jack",
            "class Main { function void run() { " + "do Output.printInt(7); return; } }");
    Path output = tempDir.resolve("out");

    assertEquals(
        "function Main.run 0\npush constant 7\ncall Output.printInt 1\n"
            + "pop temp 0\npush constant 0\nreturn\n",
        Files.readString(command.execute(source, Optional.of(output)).get(0)));
    assertFalse(Files.exists(output.resolve("MainT.xml")));
    assertFalse(Files.exists(output.resolve("MainP.xml")));
  }

  @Test
  void writesUtf8VmTextWithLfLineEndings() throws Exception {
    Path source =
        write(
            "Main.jack",
            "// comentário\r\nclass Main {\r\n" + "function void run() { return; }\r\n}\r\n");

    Path output = command.execute(source, Optional.empty()).get(0);
    String vm = Files.readString(output, StandardCharsets.UTF_8);
    assertEquals(SIMPLE_VM, vm);
    assertArrayEquals(SIMPLE_VM.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(output));
    assertFalse(vm.contains("\r"));
  }

  @Test
  void compilesStringConstantsIntoVmCalls() throws Exception {
    Path source = write("Main.jack", "class Main { function String text() { return \"Hi\"; } }");

    assertEquals(
        "function Main.text 0\npush constant 2\ncall String.new 1\n"
            + "push constant 72\ncall String.appendChar 2\npush constant 105\n"
            + "call String.appendChar 2\nreturn\n",
        Files.readString(command.execute(source, Optional.empty()).get(0)));
  }

  @Test
  void preservesDotsAndUnicodeInSourceBasename() throws Exception {
    Path source = write("Olá.test.jack", "class Main {}");

    assertEquals(
        List.of(tempDir.resolve("Olá.test.vm")), command.execute(source, Optional.empty()));
  }

  @Test
  void recompilationReplacesExistingDefaultVmOutput() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    Path vm = Files.writeString(tempDir.resolve("Main.vm"), "old vm", StandardCharsets.UTF_8);

    assertEquals(List.of(vm), command.execute(source, Optional.empty()));
    assertEquals(SIMPLE_VM, Files.readString(vm));
    assertEquals(SIMPLE, Files.readString(source));
  }

  @Test
  void defaultOutputCanBeGeneratedAgainWithUpdatedSource() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    Path first = command.execute(source, Optional.empty()).get(0);
    Files.writeString(source, "class Main { function int run() { return 9; } }");

    Path second = command.execute(source, Optional.empty()).get(0);
    assertEquals(first, second);
    assertNotEquals(SIMPLE_VM, Files.readString(second));
    assertEquals("function Main.run 0\npush constant 9\nreturn\n", Files.readString(second));
  }

  @Test
  void rejectsCustomDestinationThatAlreadyExistsBeforeWriting() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    Path out = Files.createDirectory(tempDir.resolve("out"));
    Path destination = Files.writeString(out.resolve("Main.vm"), "protected");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(source, Optional.of(out)));

    assertEquals(source, error.sourceFile());
    assertInstanceOf(FileAlreadyExistsException.class, error.getCause());
    assertEquals("protected", Files.readString(destination));
  }

  @Test
  void customOutputCannotBeRegeneratedWithoutSelectingNewDirectory() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    Path out = tempDir.resolve("out");
    command.execute(source, Optional.of(out));
    Files.writeString(source, "class Main { function int run() { return 2; } }");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(source, Optional.of(out)));

    assertInstanceOf(FileAlreadyExistsException.class, error.getCause());
    assertEquals(SIMPLE_VM, Files.readString(out.resolve("Main.vm")));
  }

  @Test
  void recursivelyCompilesNestedDirectoriesWithDefaultDestinations() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    Path first = writeUnder(root, "A.jack", "class A { function void run() { return; } }");
    Path second = writeUnder(root, "sub/B.jack", "class B { function void run() { return; } }");
    Path third =
        writeUnder(root, "sub/deeper/C.jack", "class C { function void run() { return; } }");

    assertEquals(
        List.of(
            first.resolveSibling("A.vm"),
            second.resolveSibling("B.vm"),
            third.resolveSibling("C.vm")),
        command.execute(root, Optional.empty()));
    assertTrue(Files.readString(second.resolveSibling("B.vm")).contains("function B.run 0"));
  }

  @Test
  void recursivelyCompilesOnlyNestedSourcesWhenRootHasNoJackFile() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    Path source = writeUnder(root, "deep/sub/Main.jack", SIMPLE);

    assertEquals(
        List.of(source.resolveSibling("Main.vm")), command.execute(root, Optional.empty()));
  }

  @Test
  void mirrorsNestedDirectoriesIntoCustomOutputRoot() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    writeUnder(root, "Main.jack", SIMPLE);
    writeUnder(root, "lib/math/Adder.jack", "class Adder { function int add() { return 3; } }");
    Path out = tempDir.resolve("compiled");

    assertEquals(
        List.of(out.resolve("Main.vm"), out.resolve("lib/math/Adder.vm")),
        command.execute(root, Optional.of(out)));
    assertEquals(SIMPLE_VM, Files.readString(out.resolve("Main.vm")));
    assertEquals(
        "function Adder.add 0\npush constant 3\nreturn\n",
        Files.readString(out.resolve("lib/math/Adder.vm")));
  }

  @Test
  void preservesSameBasenameInDifferentNestedDirectories() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    writeUnder(root, "alpha/Main.jack", SIMPLE);
    writeUnder(root, "beta/Main.jack", "class Main { function int run() { return 2; } }");
    Path out = tempDir.resolve("vm");

    assertEquals(
        List.of(out.resolve("alpha/Main.vm"), out.resolve("beta/Main.vm")),
        command.execute(root, Optional.of(out)));
    assertEquals(SIMPLE_VM, Files.readString(out.resolve("alpha/Main.vm")));
    assertEquals(
        "function Main.run 0\npush constant 2\nreturn\n",
        Files.readString(out.resolve("beta/Main.vm")));
  }

  @Test
  void sourceDirectoryNamedJackIsTreatedAsDirectory() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("project.jack"));
    Path source = writeUnder(root, "nested/Main.jack", SIMPLE);

    assertEquals(
        List.of(source.resolveSibling("Main.vm")), command.execute(root, Optional.empty()));
  }

  @Test
  void batchUsesRelativePathOrderingRatherThanOnlyFilenames() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    writeUnder(root, "A/Z.jack", "class Z { function void run() { return; } }");
    writeUnder(root, "B/A.jack", "class A { function void run() { return; } }");
    Path out = tempDir.resolve("out");

    assertEquals(
        List.of(out.resolve("A/Z.vm"), out.resolve("B/A.vm")),
        command.execute(root, Optional.of(out)));
  }

  @Test
  void filtersOtherExtensionsAtAllDepths() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    writeUnder(root, "Main.jack", SIMPLE);
    writeUnder(root, "Other.JACK", "class Other {}");
    writeUnder(root, "nested/Backup.jack.bak", "class Backup {}");
    writeUnder(root, "nested/Notes.txt", "text");
    Path out = tempDir.resolve("out");

    assertEquals(List.of(out.resolve("Main.vm")), command.execute(root, Optional.of(out)));
    assertFalse(Files.exists(out.resolve("Other.vm")));
    assertFalse(Files.exists(out.resolve("nested/Backup.vm")));
  }

  @Test
  void refusesToFollowSymbolicDirectoryDuringDiscovery() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    writeUnder(root, "Main.jack", SIMPLE);
    Path external = Files.createDirectory(tempDir.resolve("external"));
    writeUnder(external, "Hidden.jack", "class Hidden {}");
    Files.createSymbolicLink(root.resolve("linked"), external);

    assertEquals(List.of(root.resolve("Main.vm")), command.execute(root, Optional.empty()));
    assertFalse(Files.exists(external.resolve("Hidden.vm")));
  }

  @Test
  void cannotRecursivelyCompileSymbolicDirectoryInput() throws Exception {
    Path real = Files.createDirectory(tempDir.resolve("real"));
    writeUnder(real, "Main.jack", SIMPLE);
    Path linked = Files.createSymbolicLink(tempDir.resolve("alias"), real);

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(linked, Optional.empty()));

    assertEquals(linked, error.sourceFile());
    assertInstanceOf(IOException.class, error.getCause());
    assertFalse(Files.exists(real.resolve("Main.vm")));
  }

  @Test
  void stopsOnFirstLexicalErrorAndRetainsEarlierOutputs() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    writeUnder(root, "A.jack", "class A { function void run() { return; } }");
    Path bad = writeUnder(root, "B.jack", "class B { @ }");
    writeUnder(root, "C.jack", "class C { function void run() { return; } }");
    Path out = tempDir.resolve("out");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(root, Optional.of(out)));

    assertEquals(bad, error.sourceFile());
    assertInstanceOf(LexicalException.class, error.getCause());
    assertTrue(Files.isRegularFile(out.resolve("A.vm")));
    assertFalse(Files.exists(out.resolve("B.vm")));
    assertFalse(Files.exists(out.resolve("C.vm")));
  }

  @Test
  void stopsOnFirstSyntaxErrorAndRetainsEarlierOutputs() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    writeUnder(root, "A.jack", "class A { function void run() { return; } }");
    Path bad = writeUnder(root, "B.jack", "class B {");
    writeUnder(root, "C.jack", "class C { function void run() { return; } }");
    Path out = tempDir.resolve("out");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(root, Optional.of(out)));

    assertEquals(bad, error.sourceFile());
    assertInstanceOf(SyntaxException.class, error.getCause());
    assertTrue(Files.isRegularFile(out.resolve("A.vm")));
    assertFalse(Files.exists(out.resolve("B.vm")));
    assertFalse(Files.exists(out.resolve("C.vm")));
  }

  @Test
  void stopsOnFirstCompilationErrorAndRetainsEarlierOutputs() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    writeUnder(root, "A.jack", "class A { function void run() { return; } }");
    Path bad = writeUnder(root, "B.jack", "class B { function int run() { return missing; } }");
    writeUnder(root, "C.jack", "class C { function void run() { return; } }");
    Path out = tempDir.resolve("out");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(root, Optional.of(out)));

    assertEquals(bad, error.sourceFile());
    assertInstanceOf(CompilationException.class, error.getCause());
    assertTrue(Files.isRegularFile(out.resolve("A.vm")));
    assertFalse(Files.exists(out.resolve("B.vm")));
    assertFalse(Files.exists(out.resolve("C.vm")));
  }

  @Test
  void stopsOnMalformedUtf8AndLeavesSourceBytesUntouched() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    writeUnder(root, "A.jack", "class A { function void run() { return; } }");
    byte[] malformed = {(byte) 0xC3, (byte) 0x28};
    Path bad = Files.write(root.resolve("B.jack"), malformed);
    writeUnder(root, "C.jack", "class C { function void run() { return; } }");
    Path out = tempDir.resolve("out");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(root, Optional.of(out)));

    assertEquals(bad, error.sourceFile());
    assertInstanceOf(MalformedInputException.class, error.getCause());
    assertArrayEquals(malformed, Files.readAllBytes(bad));
    assertTrue(Files.exists(out.resolve("A.vm")));
    assertFalse(Files.exists(out.resolve("B.vm")));
    assertFalse(Files.exists(out.resolve("C.vm")));
  }

  @Test
  void doesNotReplacePreviouslyGeneratedDefaultVmWhenCompilationFails() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    Path vm = command.execute(source, Optional.empty()).get(0);
    Files.writeString(source, "class Main { function int run() { return missing; } }");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(source, Optional.empty()));

    assertInstanceOf(CompilationException.class, error.getCause());
    assertEquals(SIMPLE_VM, Files.readString(vm));
    assertNoTemporaryFiles(tempDir);
  }

  @Test
  void planningCollisionInLaterFilePreventsAllBatchOutputs() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    writeUnder(root, "A.jack", "class A { function void run() { return; } }");
    writeUnder(root, "B.jack", "class B { function void run() { return; } }");
    Path out = Files.createDirectory(tempDir.resolve("out"));
    Files.writeString(out.resolve("B.vm"), "preexisting");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(root, Optional.of(out)));

    assertEquals(root, error.sourceFile());
    assertInstanceOf(FileAlreadyExistsException.class, error.getCause());
    assertFalse(Files.exists(out.resolve("A.vm")));
    assertEquals("preexisting", Files.readString(out.resolve("B.vm")));
  }

  @Test
  void planningCollisionPrecedesSourceLexicalErrors() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    writeUnder(root, "A.jack", "@");
    writeUnder(root, "B.jack", "class B {}");
    Path out = Files.createDirectory(tempDir.resolve("out"));
    Files.writeString(out.resolve("B.vm"), "existing");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(root, Optional.of(out)));

    assertInstanceOf(FileAlreadyExistsException.class, error.getCause());
    assertFalse(Files.exists(out.resolve("A.vm")));
  }

  @Test
  void rejectsVmDestinationHardLinkedToJackSource() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    Files.createLink(tempDir.resolve("Main.vm"), source);

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(source, Optional.empty()));

    assertInstanceOf(IOException.class, error.getCause());
    assertTrue(error.getCause().getMessage().contains("aliases a Jack source"));
    assertEquals(SIMPLE, Files.readString(source));
  }

  @Test
  void rejectsVmDestinationAliasingAnotherSourceInBatch() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    writeUnder(root, "A.jack", "class A {}");
    Path b = writeUnder(root, "B.jack", "class B {}");
    Files.createLink(root.resolve("A.vm"), b);

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(root, Optional.empty()));

    assertEquals(root, error.sourceFile());
    assertInstanceOf(IOException.class, error.getCause());
    assertTrue(error.getCause().getMessage().contains("aliases a Jack source"));
    assertFalse(Files.exists(root.resolve("B.vm")));
  }

  @Test
  void rejectsSymbolicOutputFileAndPreservesTarget() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    Path protectedFile = Files.writeString(tempDir.resolve("protected.vm"), "protected");
    Files.createSymbolicLink(tempDir.resolve("Main.vm"), protectedFile);

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(source, Optional.empty()));

    assertInstanceOf(IOException.class, error.getCause());
    assertEquals("protected", Files.readString(protectedFile));
  }

  @Test
  void rejectsSymbolicOutputDirectoryBeforeWriting() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    Path real = Files.createDirectory(tempDir.resolve("real"));
    Path link = Files.createSymbolicLink(tempDir.resolve("link"), real);

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(source, Optional.of(link)));

    assertInstanceOf(IOException.class, error.getCause());
    assertFalse(Files.exists(real.resolve("Main.vm")));
  }

  @Test
  void rejectsProtectedSourceDirectoryOutput() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    Path protectedDirectory = tempDir.resolve("src/main/java/generated");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(protectedDirectory)));

    assertInstanceOf(IOException.class, error.getCause());
    assertFalse(Files.exists(protectedDirectory.resolve("Main.vm")));
  }

  @Test
  void rejectsProtectedFixtureDirectoryOutput() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    Path protectedDirectory = tempDir.resolve("src/test/resources/examples");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(protectedDirectory)));

    assertInstanceOf(IOException.class, error.getCause());
    assertFalse(Files.exists(protectedDirectory.resolve("Main.vm")));
  }

  @Test
  void rejectsOutputParentOccupiedByRegularFile() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    Path outputParent = Files.writeString(tempDir.resolve("not-a-directory"), "original");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class,
            () -> command.execute(source, Optional.of(outputParent)));

    assertInstanceOf(IOException.class, error.getCause());
    assertEquals("original", Files.readString(outputParent));
  }

  @Test
  void rejectsDestinationOccupiedByDirectory() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    Files.createDirectory(tempDir.resolve("Main.vm"));

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(source, Optional.empty()));

    assertInstanceOf(IOException.class, error.getCause());
    assertTrue(Files.isDirectory(tempDir.resolve("Main.vm")));
  }

  @Test
  void rejectsMissingInputWithOriginalPath() {
    Path missing = tempDir.resolve("Missing.jack");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(missing, Optional.empty()));

    assertEquals(missing, error.sourceFile());
    assertInstanceOf(NoSuchFileException.class, error.getCause());
  }

  @Test
  void rejectsDirectoryWithNoJackSources() throws Exception {
    Path input = Files.createDirectory(tempDir.resolve("sources"));
    writeUnder(input, "README.md", "documentation");
    writeUnder(input, "Nested/A.JACK", "class A {}");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(input, Optional.empty()));

    assertEquals(input, error.sourceFile());
    assertInstanceOf(IOException.class, error.getCause());
    assertTrue(error.getMessage().contains("No .jack files"));
  }

  @Test
  void rejectsEmptyDirectory() throws Exception {
    Path input = Files.createDirectory(tempDir.resolve("sources"));

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(input, Optional.empty()));

    assertInstanceOf(IOException.class, error.getCause());
  }

  @ParameterizedTest
  @ValueSource(strings = {"Main.txt", "Main.JACK", "Main.jack.bak"})
  void rejectsIndividualFilesWithoutExactJackExtension(String filename) throws Exception {
    Path source = write(filename, SIMPLE);

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(source, Optional.empty()));

    assertEquals(source, error.sourceFile());
    assertInstanceOf(IllegalArgumentException.class, error.getCause());
  }

  @Test
  void rejectsMalformedSyntaxWithoutPublishingOutput() throws Exception {
    Path source = write("Main.jack", "class Main {");
    Path out = tempDir.resolve("out");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(source, Optional.of(out)));

    assertInstanceOf(SyntaxException.class, error.getCause());
    assertEquals(source, error.sourceFile());
    assertFalse(Files.exists(out));
  }

  @Test
  void reportsOriginalLexicalErrorPositionAndFilename() throws Exception {
    Path source = write("Main.jack", "class Main {}\n@");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(source, Optional.empty()));

    LexicalException lexical = assertInstanceOf(LexicalException.class, error.getCause());
    assertEquals(source, error.sourceFile());
    assertEquals(2, lexical.position().line());
    assertEquals(1, lexical.position().column());
    assertTrue(error.getMessage().contains(source.toString()));
    assertFalse(Files.exists(tempDir.resolve("Main.vm")));
  }

  @Test
  void reportsOriginalCompilationErrorPositionAndFilename() throws Exception {
    Path source =
        write("Main.jack", "class Main {\n" + "  function int bad() { return missing; }\n}");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(source, Optional.empty()));

    CompilationException compilation =
        assertInstanceOf(CompilationException.class, error.getCause());
    assertEquals(source, error.sourceFile());
    assertEquals(2, compilation.position().orElseThrow().line());
    assertTrue(error.getMessage().contains(source.toString()));
    assertFalse(Files.exists(tempDir.resolve("Main.vm")));
  }

  @Test
  void rejectsConstructorWithWrongDeclaredReturnType() throws Exception {
    Path source = write("Main.jack", "class Main { constructor int new() { return 0; } }");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(source, Optional.empty()));

    CompilationException compilation =
        assertInstanceOf(CompilationException.class, error.getCause());
    assertTrue(compilation.description().contains("Constructor must declare its enclosing class"));
  }

  @Test
  void detectsDuplicateSymbolsWithoutPublishingVm() throws Exception {
    Path source = write("Main.jack", "class Main { static int n; field int n; }");

    SourceProcessingException error =
        assertThrows(
            SourceProcessingException.class, () -> command.execute(source, Optional.empty()));

    assertInstanceOf(CompilationException.class, error.getCause());
    assertFalse(Files.exists(tempDir.resolve("Main.vm")));
  }

  @Test
  void keepsPreviouslyPublishedOutputsWhenLaterFileFailsUnderDefaultPolicy() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    writeUnder(root, "A.jack", "class A { function void run() { return; } }");
    writeUnder(root, "B.jack", "class B { function int run() { return missing; } }");
    Path existingA = Files.writeString(root.resolve("A.vm"), "old-A");
    Path existingB = Files.writeString(root.resolve("B.vm"), "old-B");

    assertThrows(SourceProcessingException.class, () -> command.execute(root, Optional.empty()));

    assertTrue(Files.readString(existingA).contains("function A.run 0"));
    assertEquals("old-B", Files.readString(existingB));
  }

  @Test
  void noTemporaryFilesRemainAfterSuccessfulCompilation() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    command.execute(source, Optional.empty());
    assertNoTemporaryFiles(tempDir);
  }

  @Test
  void noTemporaryFilesRemainAfterOutputCollision() throws Exception {
    Path source = write("Main.jack", SIMPLE);
    Path out = Files.createDirectory(tempDir.resolve("out"));
    Files.writeString(out.resolve("Main.vm"), "occupied");

    assertThrows(SourceProcessingException.class, () -> command.execute(source, Optional.of(out)));
    assertNoTemporaryFiles(out);
  }

  @Test
  void repeatedExecutionsUseIndependentClassState() throws Exception {
    Path first =
        write(
            "First.jack",
            "class First { field int x, y; " + "constructor First new() { return this; } }");
    Path second =
        write(
            "Second.jack",
            "class Second { field int x; " + "constructor Second new() { return this; } }");

    Path firstVm = command.execute(first, Optional.empty()).get(0);
    Path secondVm = command.execute(second, Optional.empty()).get(0);

    assertTrue(Files.readString(firstVm).contains("push constant 2\ncall Memory.alloc 1"));
    assertTrue(Files.readString(secondVm).contains("push constant 1\ncall Memory.alloc 1"));
  }

  @Test
  void sourceIsUnchangedAfterSuccessfulCompilation() throws Exception {
    String text = "// comment\n" + SIMPLE + "\n";
    Path source = write("Main.jack", text);
    byte[] original = Files.readAllBytes(source);

    command.execute(source, Optional.of(tempDir.resolve("out")));

    assertArrayEquals(original, Files.readAllBytes(source));
  }

  @Test
  void rejectsNullInput() {
    assertThrows(NullPointerException.class, () -> command.execute(null, Optional.empty()));
  }

  @Test
  void rejectsNullOutputDirectoryOptional() {
    assertThrows(
        NullPointerException.class, () -> command.execute(tempDir.resolve("Main.jack"), null));
  }

  private Path write(String filename, String text) throws IOException {
    return Files.writeString(tempDir.resolve(filename), text, StandardCharsets.UTF_8);
  }

  private static Path writeUnder(Path root, String relative, String text) throws IOException {
    Path destination = root.resolve(relative);
    Files.createDirectories(destination.getParent());
    return Files.writeString(destination, text, StandardCharsets.UTF_8);
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
