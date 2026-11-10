package jackcompiler.acceptance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.application.CompileCommand;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Acceptance tests for the official nand2tetris Project 11 Jack programs.
 *
 * <p>Sources originate from nand2tetris/projects, revision
 * 6097b770073025891fb8b7a22aa3b72f61f81277. The upstream MIT license is preserved with the test
 * resources.
 *
 * <p>These tests verify VM generation, not execution in the VM Emulator.
 */
class OfficialVmGenerationTest {

  private static final String RESOURCE_ROOT = "/nand2tetris/project11/";

  @TempDir Path tempDir;

  @ParameterizedTest(name = "{0}: official Jack-to-VM generation")
  @ValueSource(strings = {"Seven", "Average", "ConvertToBin", "ComplexArrays"})
  void compilesOfficialSourceWithoutModifyingInput(String program) throws Exception {
    Path directory = Files.createDirectory(tempDir.resolve(program));
    Path source = directory.resolve("Main.jack");
    Path output = directory.resolve("Main.vm");

    byte[] original = readOfficialSource(program);
    Files.write(source, original);

    List<Path> generated = new CompileCommand().execute(source, Optional.empty());

    assertEquals(List.of(output), generated);
    assertTrue(Files.isRegularFile(output));

    String vm = Files.readString(output, StandardCharsets.UTF_8);

    assertFalse(vm.isBlank());
    assertTrue(vm.startsWith("function Main.main "));
    assertTrue(vm.endsWith("return\n"));
    assertFalse(vm.contains("\r"));

    assertArrayEquals(original, Files.readAllBytes(source));
    assertFalse(Files.exists(directory.resolve("MainT.xml")));
    assertFalse(Files.exists(directory.resolve("MainP.xml")));
  }

  @Test
  void sevenGeneratesArithmeticAndOutputInstructions() throws Exception {
    String vm = compileOfficialProgram("Seven");

    assertTrue(vm.contains("function Main.main 0\n"));
    assertTrue(vm.contains("call Math.multiply 2\n"));
    assertTrue(vm.contains("call Output.printInt 1\n"));
    assertTrue(vm.contains("pop temp 0\n"));
    assertTrue(vm.contains("push constant 0\nreturn\n"));

    assertTrue(vm.indexOf("call Math.multiply 2\n") < vm.indexOf("call Output.printInt 1\n"));
  }

  @Test
  void averageGeneratesArrayInputAndArithmeticInstructions() throws Exception {
    String vm = compileOfficialProgram("Average");

    assertTrue(vm.contains("function Main.main 4\n"));
    assertTrue(vm.contains("call Keyboard.readInt 1\n"));
    assertTrue(vm.contains("call Array.new 1\n"));
    assertTrue(vm.contains("pop that 0\n"));
    assertTrue(vm.contains("call String.new 1\n"));
    assertTrue(vm.contains("call String.appendChar 2\n"));
    assertTrue(vm.contains("call Math.divide 2\n"));
    assertTrue(vm.contains("call Output.printString 1\n"));
    assertTrue(vm.contains("call Output.printInt 1\n"));

    assertTrue(vm.indexOf("call Math.divide 2\n") < vm.lastIndexOf("call Output.printInt 1\n"));
  }

  @Test
  void convertToBinGeneratesMemoryAndBitMaskInstructions() throws Exception {
    String vm = compileOfficialProgram("ConvertToBin");

    assertTrue(vm.contains("function Main.main 1\n"));
    assertTrue(vm.contains("function Main.convert 3\n"));
    assertTrue(vm.contains("function Main.nextMask 0\n"));
    assertTrue(vm.contains("function Main.fillMemory 0\n"));

    assertTrue(vm.contains("call Main.fillMemory 3\n"));
    assertTrue(vm.contains("call Memory.peek 1\n"));
    assertTrue(vm.contains("call Main.convert 1\n"));
    assertTrue(vm.contains("call Main.nextMask 1\n"));
    assertTrue(vm.contains("call Memory.poke 2\n"));
    assertTrue(vm.contains("call Math.multiply 2\n"));

    assertTrue(vm.indexOf("call Memory.peek 1\n") < vm.indexOf("call Main.convert 1\n"));
  }

  @Test
  void complexArraysGeneratesIndirectAddressingAndNestedCalls() throws Exception {
    String vm = compileOfficialProgram("ComplexArrays");

    assertTrue(vm.contains("function Main.main 3\n"));
    assertTrue(vm.contains("function Main.double 0\n"));
    assertTrue(vm.contains("function Main.fill 0\n"));

    assertTrue(vm.contains("call Array.new 1\n"));
    assertTrue(vm.contains("call Main.double 1\n"));
    assertTrue(vm.contains("call Main.fill 2\n"));
    assertTrue(vm.contains("call Math.multiply 2\n"));

    assertTrue(vm.contains("push that 0\n"));
    assertTrue(vm.contains("pop pointer 1\n"));
    assertTrue(vm.contains("pop that 0\n"));

    assertTrue(vm.contains("call Output.printString 1\n"));
    assertTrue(vm.contains("call Output.printInt 1\n"));
    assertTrue(vm.contains("call Output.println 0\n"));
  }

  private String compileOfficialProgram(String program) throws Exception {
    Path directory = Files.createDirectory(tempDir.resolve(program));
    Path source = directory.resolve("Main.jack");
    Path output = directory.resolve("Main.vm");

    Files.write(source, readOfficialSource(program));

    assertEquals(List.of(output), new CompileCommand().execute(source, Optional.empty()));

    return Files.readString(output, StandardCharsets.UTF_8);
  }

  private static byte[] readOfficialSource(String program) throws IOException {
    String resource = RESOURCE_ROOT + program + "/Main.jack";

    try (InputStream input = OfficialVmGenerationTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("Official Project 11 source missing: " + resource);
      }

      return input.readAllBytes();
    }
  }
}
