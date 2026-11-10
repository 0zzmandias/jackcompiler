package jackcompiler.integration;

import static jackcompiler.integration.JarExecutionTestSupport.assertNoTemporaryFiles;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.integration.JarExecutionTestSupport.Execution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests Jack-to-VM compilation through the executable JAR in a separate JVM. */
class JarCompileIT {

  @TempDir Path tempDir;

  @Test
  void packagedJarCompilesStandaloneJackIntoCustomDirectory() throws Exception {
    String sourceText = "class Main { function void run() { return; } }";
    Path source = Files.writeString(tempDir.resolve("Main.jack"), sourceText);
    byte[] original = Files.readAllBytes(source);
    Path out = tempDir.resolve("vm-output");

    Execution result =
        execute(tempDir, "compile", source.toString(), "--output-dir", out.toString());

    Path vm = out.resolve("Main.vm");
    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals("Generated: " + vm + System.lineSeparator(), result.stdout());
    assertEquals("function Main.run 0\npush constant 0\nreturn\n", Files.readString(vm));
    assertArrayEquals(original, Files.readAllBytes(source));
    assertFalse(Files.exists(out.resolve("MainT.xml")));
    assertFalse(Files.exists(out.resolve("MainP.xml")));
  }

  @Test
  void packagedJarWritesDefaultVmBesideRelativeJackSource() throws Exception {
    Path working = Files.createDirectory(tempDir.resolve("vm-working"));
    Files.writeString(
        working.resolve("Main.jack"), "class Main { function int run() { return 7; } }");

    Execution result = execute(working, "compile", "Main.jack");

    Path vm = working.resolve("Main.vm");
    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals("Generated: " + vm + System.lineSeparator(), result.stdout());
    assertEquals("function Main.run 0\npush constant 7\nreturn\n", Files.readString(vm));
    assertFalse(Files.exists(working.resolve("target/generated-parser/MainP.xml")));
    assertFalse(Files.exists(working.resolve("target/generated-tokens/MainT.xml")));
  }

  @Test
  void packagedJarCompilesNestedDirectoryRecursively() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    Files.writeString(root.resolve("A.jack"), "class A { function void run() { return; } }");
    Path nested = Files.createDirectories(root.resolve("lib/math"));
    Files.writeString(
        nested.resolve("Adder.jack"), "class Adder { function int add() { return 3; } }");
    Path out = tempDir.resolve("vm-out");

    Execution result = execute(tempDir, "compile", root.toString(), "--output-dir", out.toString());

    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals(
        "Generated: "
            + out.resolve("A.vm")
            + System.lineSeparator()
            + "Generated: "
            + out.resolve("lib/math/Adder.vm")
            + System.lineSeparator(),
        result.stdout());
    assertTrue(Files.readString(out.resolve("A.vm")).contains("function A.run 0"));
    assertTrue(Files.readString(out.resolve("lib/math/Adder.vm")).contains("function Adder.add 0"));
  }

  @Test
  void packagedJarCompilesNestedDirectoryBesideSourcesByDefault() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    Path first =
        Files.writeString(root.resolve("A.jack"), "class A { function void run() { return; } }");
    Path nested = Files.createDirectory(root.resolve("nested"));
    Path second =
        Files.writeString(nested.resolve("B.jack"), "class B { function void run() { return; } }");

    Execution result = execute(tempDir, "compile", root.toString());

    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals(
        "Generated: "
            + first.resolveSibling("A.vm")
            + System.lineSeparator()
            + "Generated: "
            + second.resolveSibling("B.vm")
            + System.lineSeparator(),
        result.stdout());
    assertTrue(Files.isRegularFile(root.resolve("A.vm")));
    assertTrue(Files.isRegularFile(nested.resolve("B.vm")));
  }

  @Test
  void packagedJarPreservesExistingDefaultVmAfterCompilationFailure() throws Exception {
    Path source =
        Files.writeString(
            tempDir.resolve("Main.jack"), "class Main { function int run() { return 1; } }");

    Execution first = execute(tempDir, "compile", source.toString());
    assertEquals(0, first.exitCode());

    Path vm = tempDir.resolve("Main.vm");
    String originalVm = Files.readString(vm);

    Files.writeString(source, "class Main { function int run() { return missing; } }");

    Execution failure = execute(tempDir, "compile", source.toString());

    assertEquals(1, failure.exitCode());
    assertEquals("", failure.stdout());
    assertTrue(failure.stderr().contains("Compilation error"));
    assertEquals(originalVm, Files.readString(vm));
    assertNoTemporaryFiles(tempDir);
  }

  @Test
  void packagedJarRejectsCustomVmCollisionBeforeAnyOutput() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    Files.writeString(root.resolve("A.jack"), "class A {}");
    Files.writeString(root.resolve("B.jack"), "class B {}");
    Path out = Files.createDirectory(tempDir.resolve("vm-out"));
    Files.writeString(out.resolve("B.vm"), "protected");

    Execution result = execute(tempDir, "compile", root.toString(), "--output-dir", out.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertFalse(Files.exists(out.resolve("A.vm")));
    assertEquals("protected", Files.readString(out.resolve("B.vm")));
  }

  @Test
  void packagedJarStopsVmCompilationAtFirstSemanticFailure() throws Exception {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    Files.writeString(root.resolve("A.jack"), "class A { function void run() { return; } }");
    Files.writeString(root.resolve("B.jack"), "class B { function int run() { return missing; } }");
    Files.writeString(root.resolve("C.jack"), "class C { function void run() { return; } }");
    Path out = tempDir.resolve("vm-out");

    Execution result = execute(tempDir, "compile", root.toString(), "--output-dir", out.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Compilation error"));
    assertTrue(result.stderr().contains("B.jack"));
    assertTrue(Files.isRegularFile(out.resolve("A.vm")));
    assertFalse(Files.exists(out.resolve("B.vm")));
    assertFalse(Files.exists(out.resolve("C.vm")));
  }

  @Test
  void packagedJarReportsVmCompilationErrorWithSourcePosition() throws Exception {
    Path source =
        Files.writeString(
            tempDir.resolve("Main.jack"),
            "class Main {\n  function int run() { return missing; }\n}");
    Path out = tempDir.resolve("vm-out");

    Execution result =
        execute(tempDir, "compile", source.toString(), "--output-dir", out.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Compilation error"));
    assertTrue(result.stderr().contains(source.toString()));
    assertTrue(result.stderr().contains("line 2, column"));
    assertFalse(Files.exists(out.resolve("Main.vm")));
  }

  @Test
  void packagedJarReportsSyntaxErrorDuringVmCompilation() throws Exception {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {");

    Execution result = execute(tempDir, "compile", source.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Syntax error"));
    assertTrue(result.stderr().contains("line 1, column 13"));
    assertFalse(Files.exists(tempDir.resolve("Main.vm")));
  }

  @Test
  void packagedJarReportsLexicalErrorDuringVmCompilation() throws Exception {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}\n@");

    Execution result = execute(tempDir, "compile", source.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Lexical error"));
    assertTrue(result.stderr().contains("line 2, column 1"));
    assertFalse(Files.exists(tempDir.resolve("Main.vm")));
  }

  @Test
  void packagedJarRejectsMalformedUtf8DuringVmCompilation() throws Exception {
    Path source = tempDir.resolve("Malformed.jack");
    byte[] original = {(byte) 0xC3, (byte) 0x28};
    Files.write(source, original);

    Execution result = execute(tempDir, "compile", source.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertFalse(result.stderr().contains("line 1, column"));
    assertArrayEquals(original, Files.readAllBytes(source));
    assertFalse(Files.exists(tempDir.resolve("Malformed.vm")));
  }

  @Test
  void packagedJarRejectsSymbolicVmOutputDestination() throws Exception {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");
    Path protectedFile = Files.writeString(tempDir.resolve("protected.vm"), "protected");
    Files.createSymbolicLink(tempDir.resolve("Main.vm"), protectedFile);

    Execution result = execute(tempDir, "compile", source.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertEquals("protected", Files.readString(protectedFile));
  }

  @Test
  void packagedJarRejectsCompileWithoutInput() throws Exception {
    Execution result = execute(tempDir, "compile");

    assertEquals(2, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Compile requires an input path"));
    assertTrue(result.stderr().contains("Usage:"));
  }

  @Test
  void packagedJarRejectsUnknownCompileOption() throws Exception {
    Execution result = execute(tempDir, "compile", "Main.jack", "--unknown");

    assertEquals(2, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Unknown option"));
  }

  @Test
  void packagedJarRejectsMissingCompileSource() throws Exception {
    Path missing = tempDir.resolve("Missing.jack");

    Execution result = execute(tempDir, "compile", missing.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertTrue(result.stderr().contains(missing.toString()));
  }

  private Execution execute(Path workingDirectory, String... arguments)
      throws IOException, InterruptedException {
    return JarExecutionTestSupport.execute(tempDir, workingDirectory, arguments);
  }
}
