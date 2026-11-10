package jackcompiler.cli;

import static jackcompiler.cli.CliIntegrationTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.cli.CliIntegrationTestSupport.Execution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the compile operation through the CLI entry point. */
class CliCompileIntegrationTest {

  @TempDir Path tempDir;

  @Test
  void compilesSingleSourceAndReportsVmDestination() throws IOException {
    String sourceText = "class Main { function void run() { return; } }";
    Path source = write("Main.jack", sourceText);
    Path out = tempDir.resolve("vm-out");

    Execution result = execute("compile", source.toString(), "--output-dir", out.toString());

    Path vm = out.resolve("Main.vm");
    assertEquals(0, result.status());
    assertEquals("Generated: " + vm + "\n", result.stdout());
    assertEquals("", result.stderr());
    assertEquals("function Main.run 0\npush constant 0\nreturn\n", Files.readString(vm));
    assertEquals(sourceText, Files.readString(source));
    assertFalse(Files.exists(out.resolve("MainT.xml")));
    assertFalse(Files.exists(out.resolve("MainP.xml")));
  }

  @Test
  void compileUsesSourceDirectoryByDefault() throws IOException {
    Path source = write("Main.jack", "class Main { function void run() { return; } }");

    Execution result = execute("compile", source.toString());

    Path vm = tempDir.resolve("Main.vm");
    assertEquals(0, result.status());
    assertEquals("Generated: " + vm + "\n", result.stdout());
    assertEquals("", result.stderr());
    assertTrue(Files.readString(vm).startsWith("function Main.run 0\n"));
  }

  @Test
  void compileReplacesDefaultVmAndPreservesItAfterFailure() throws IOException {
    Path source = write("Main.jack", "class Main { function int run() { return 1; } }");
    Path vm = tempDir.resolve("Main.vm");

    assertEquals(0, execute("compile", source.toString()).status());
    assertTrue(Files.readString(vm).contains("push constant 1\n"));

    Files.writeString(source, "class Main { function int run() { return 2; } }");
    assertEquals(0, execute("compile", source.toString()).status());
    String previous = Files.readString(vm);
    assertTrue(previous.contains("push constant 2\n"));

    Files.writeString(source, "class Main { function int run() { return missing; } }");
    Execution failed = execute("compile", source.toString());

    assertEquals(1, failed.status());
    assertEquals("", failed.stdout());
    assertTrue(failed.stderr().contains("Compilation error"));
    assertEquals(previous, Files.readString(vm));
  }

  @Test
  void compileRecursesAndMirrorsSubdirectories() throws IOException {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    Files.writeString(root.resolve("Main.jack"), "class Main { function void run() { return; } }");
    Path nested = Files.createDirectories(root.resolve("lib/math"));
    Files.writeString(
        nested.resolve("Adder.jack"), "class Adder { function int sum() { return 3; } }");
    Path out = tempDir.resolve("vm-out");

    Execution result = execute("compile", root.toString(), "--output-dir", out.toString());

    assertEquals(0, result.status());
    assertEquals("", result.stderr());
    assertEquals(
        "Generated: "
            + out.resolve("Main.vm")
            + "\n"
            + "Generated: "
            + out.resolve("lib/math/Adder.vm")
            + "\n",
        result.stdout());
    assertTrue(Files.readString(out.resolve("Main.vm")).contains("function Main.run 0"));
    assertTrue(Files.readString(out.resolve("lib/math/Adder.vm")).contains("function Adder.sum 0"));
  }

  @Test
  void compilePreservesEqualBasenamesInSeparateSubdirectories() throws IOException {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    Path one = Files.createDirectories(root.resolve("one"));
    Path two = Files.createDirectories(root.resolve("two"));
    Files.writeString(one.resolve("Main.jack"), "class Main { function int run() { return 1; } }");
    Files.writeString(two.resolve("Main.jack"), "class Main { function int run() { return 2; } }");
    Path out = tempDir.resolve("out");

    Execution result = execute("compile", root.toString(), "--output-dir", out.toString());

    assertEquals(0, result.status());
    assertEquals("", result.stderr());
    assertTrue(Files.readString(out.resolve("one/Main.vm")).contains("push constant 1\n"));
    assertTrue(Files.readString(out.resolve("two/Main.vm")).contains("push constant 2\n"));
  }

  @Test
  void compileReportsSemanticErrorAtOriginalSourcePosition() throws IOException {
    Path source = write("Main.jack", "class Main {\n  function int run() { return missing; }\n}");
    Path out = tempDir.resolve("out");

    Execution result = execute("compile", source.toString(), "--output-dir", out.toString());

    assertEquals(1, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Compilation error"));
    assertTrue(result.stderr().contains(source.toString()));
    assertTrue(result.stderr().contains("line 2, column"));
    assertFalse(Files.exists(out.resolve("Main.vm")));
  }

  @Test
  void compileReportsLexicalErrorAtOriginalSourcePosition() throws IOException {
    Path source = write("Main.jack", "class Main {}\n@");

    Execution result = execute("compile", source.toString());

    assertEquals(1, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Lexical error"));
    assertTrue(result.stderr().contains("line 2, column 1"));
    assertFalse(Files.exists(tempDir.resolve("Main.vm")));
  }

  @Test
  void compileReportsSyntaxErrorAtOriginalSourcePosition() throws IOException {
    Path source = write("Main.jack", "class Main {");

    Execution result = execute("compile", source.toString());

    assertEquals(1, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Syntax error"));
    assertTrue(result.stderr().contains("line 1, column 13"));
    assertFalse(Files.exists(tempDir.resolve("Main.vm")));
  }

  @Test
  void compileRejectsMalformedUtf8AsIoError() throws IOException {
    Path source = tempDir.resolve("Malformed.jack");
    byte[] invalid = {(byte) 0xC3, (byte) 0x28};
    Files.write(source, invalid);

    Execution result = execute("compile", source.toString());

    assertEquals(1, result.status());
    assertTrue(result.stderr().contains("I/O error"));
    assertTrue(result.stderr().contains("Malformed.jack"));
    assertFalse(result.stderr().contains("line 1, column"));
    assertArrayEquals(invalid, Files.readAllBytes(source));
    assertFalse(Files.exists(tempDir.resolve("Malformed.vm")));
  }

  @Test
  void compileRejectsCustomOutputCollisionBeforeBatchWrites() throws IOException {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    Files.writeString(root.resolve("A.jack"), "class A {}");
    Files.writeString(root.resolve("B.jack"), "class B {}");
    Path out = Files.createDirectory(tempDir.resolve("out"));
    Path occupied = Files.writeString(out.resolve("B.vm"), "protected");

    Execution result = execute("compile", root.toString(), "--output-dir", out.toString());

    assertEquals(1, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertTrue(result.stderr().contains(root.toString()));
    assertFalse(Files.exists(out.resolve("A.vm")));
    assertEquals("protected", Files.readString(occupied));
  }

  @Test
  void compileStopsAtFirstSemanticFailureAndKeepsEarlierVm() throws IOException {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    Files.writeString(root.resolve("A.jack"), "class A { function void run() { return; } }");
    Files.writeString(root.resolve("B.jack"), "class B { function int run() { return missing; } }");
    Files.writeString(root.resolve("C.jack"), "class C { function void run() { return; } }");
    Path out = tempDir.resolve("out");

    Execution result = execute("compile", root.toString(), "--output-dir", out.toString());

    assertEquals(1, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Compilation error"));
    assertTrue(result.stderr().contains("B.jack"));
    assertTrue(Files.isRegularFile(out.resolve("A.vm")));
    assertFalse(Files.exists(out.resolve("B.vm")));
    assertFalse(Files.exists(out.resolve("C.vm")));
  }

  @Test
  void compileRejectsMissingSourceAsProcessingFailure() {
    Path missing = tempDir.resolve("Missing.jack");

    Execution result = execute("compile", missing.toString());

    assertEquals(1, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertTrue(result.stderr().contains(missing.toString()));
  }

  @Test
  void compileRejectsDirectoryWithNoJackSources() throws IOException {
    Path root = Files.createDirectory(tempDir.resolve("sources"));
    Files.writeString(root.resolve("README.md"), "documentation");

    Execution result = execute("compile", root.toString());

    assertEquals(1, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("No .jack files"));
  }

  @Test
  void compileRejectsMissingInputAsArgumentError() {
    Execution result = execute("compile");

    assertEquals(2, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Compile requires an input path"));
    assertTrue(result.stderr().contains("Usage:"));
  }

  @Test
  void compileRejectsUnknownOptionAsArgumentError() {
    Execution result = execute("compile", "Main.jack", "--unknown");

    assertEquals(2, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Unknown option"));
  }

  @Test
  void compileRejectsInvalidInputPathAsArgumentError() {
    Execution result = execute("compile", "bad\0path");

    assertEquals(2, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("Invalid input path"));
  }

  @Test
  void compileRejectsSymbolicVmDestination() throws IOException {
    Path source = write("Main.jack", "class Main {}");
    Path protectedFile = Files.writeString(tempDir.resolve("protected.vm"), "protected");
    Files.createSymbolicLink(tempDir.resolve("Main.vm"), protectedFile);

    Execution result = execute("compile", source.toString());

    assertEquals(1, result.status());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertEquals("protected", Files.readString(protectedFile));
  }

  private Path write(String name, String source) throws IOException {
    return CliIntegrationTestSupport.write(tempDir, name, source);
  }
}
