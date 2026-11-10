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

  private static final List<String> SQUARE_CLASSES = List.of("Main", "Square", "SquareGame");

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

  @Test
  void squareCompilesAllThreeOfficialClassesBesideSources() throws Exception {
    Path sourceDirectory = Files.createDirectory(tempDir.resolve("Square"));
    writeSquareSources(sourceDirectory);

    List<Path> expectedOutputs =
        SQUARE_CLASSES.stream().map(name -> sourceDirectory.resolve(name + ".vm")).toList();

    List<Path> generated = new CompileCommand().execute(sourceDirectory, Optional.empty());

    assertEquals(expectedOutputs, generated);

    for (String name : SQUARE_CLASSES) {
      Path source = sourceDirectory.resolve(name + ".jack");
      Path output = sourceDirectory.resolve(name + ".vm");

      assertTrue(Files.isRegularFile(output));
      assertTrue(
          Files.readString(output, StandardCharsets.UTF_8).contains("function " + name + "."));
      assertFalse(Files.readString(output, StandardCharsets.UTF_8).contains("\r"));

      assertArrayEquals(readOfficialSource("Square", name + ".jack"), Files.readAllBytes(source));

      assertFalse(Files.exists(sourceDirectory.resolve(name + "T.xml")));
      assertFalse(Files.exists(sourceDirectory.resolve(name + "P.xml")));
    }
  }

  @Test
  void squareGeneratesConstructorsMethodsAndInteractiveCalls() throws Exception {
    Path sourceDirectory = Files.createDirectory(tempDir.resolve("Square"));
    writeSquareSources(sourceDirectory);

    Path outputDirectory = tempDir.resolve("generated-square");

    List<Path> expectedOutputs =
        SQUARE_CLASSES.stream().map(name -> outputDirectory.resolve(name + ".vm")).toList();

    List<Path> generated =
        new CompileCommand().execute(sourceDirectory, Optional.of(outputDirectory));

    assertEquals(expectedOutputs, generated);

    for (String name : SQUARE_CLASSES) {
      assertTrue(Files.isRegularFile(outputDirectory.resolve(name + ".vm")));
      assertFalse(Files.exists(sourceDirectory.resolve(name + ".vm")));
      assertArrayEquals(
          readOfficialSource("Square", name + ".jack"),
          Files.readAllBytes(sourceDirectory.resolve(name + ".jack")));
    }

    String main = Files.readString(outputDirectory.resolve("Main.vm"), StandardCharsets.UTF_8);
    String square = Files.readString(outputDirectory.resolve("Square.vm"), StandardCharsets.UTF_8);
    String game =
        Files.readString(outputDirectory.resolve("SquareGame.vm"), StandardCharsets.UTF_8);

    assertTrue(main.contains("function Main.main 1\n"));
    assertTrue(main.contains("call SquareGame.new 0\n"));
    assertTrue(main.contains("call SquareGame.run 1\n"));
    assertTrue(main.contains("call SquareGame.dispose 1\n"));

    assertTrue(square.contains("function Square.new 0\n"));
    assertTrue(square.contains("call Memory.alloc 1\n"));
    assertTrue(square.contains("pop pointer 0\n"));
    assertTrue(square.contains("function Square.dispose 0\n"));
    assertTrue(square.contains("function Square.draw 0\n"));
    assertTrue(square.contains("function Square.erase 0\n"));
    assertTrue(square.contains("function Square.incSize 0\n"));
    assertTrue(square.contains("function Square.decSize 0\n"));
    assertTrue(square.contains("function Square.moveUp 0\n"));
    assertTrue(square.contains("function Square.moveDown 0\n"));
    assertTrue(square.contains("function Square.moveLeft 0\n"));
    assertTrue(square.contains("function Square.moveRight 0\n"));
    assertTrue(square.contains("call Screen.drawRectangle 4\n"));

    assertTrue(game.contains("function SquareGame.new 0\n"));
    assertTrue(game.contains("call Square.new 3\n"));
    assertTrue(game.contains("function SquareGame.dispose 0\n"));
    assertTrue(game.contains("call Square.dispose 1\n"));
    assertTrue(game.contains("function SquareGame.moveSquare 0\n"));
    assertTrue(game.contains("function SquareGame.run 2\n"));
    assertTrue(game.contains("call Keyboard.keyPressed 0\n"));
    assertTrue(game.contains("call SquareGame.moveSquare 1\n"));
    assertTrue(game.contains("call Square.moveUp 1\n"));
    assertTrue(game.contains("call Square.moveDown 1\n"));
    assertTrue(game.contains("call Square.moveLeft 1\n"));
    assertTrue(game.contains("call Square.moveRight 1\n"));
    assertTrue(game.contains("call Square.incSize 1\n"));
    assertTrue(game.contains("call Square.decSize 1\n"));
    assertTrue(game.contains("call Sys.wait 1\n"));

    assertTrue(main.indexOf("call SquareGame.new 0\n") < main.indexOf("call SquareGame.run 1\n"));
    assertTrue(
        main.indexOf("call SquareGame.run 1\n") < main.indexOf("call SquareGame.dispose 1\n"));
  }

  private void writeSquareSources(Path directory) throws IOException {
    for (String name : SQUARE_CLASSES) {
      Files.write(directory.resolve(name + ".jack"), readOfficialSource("Square", name + ".jack"));
    }
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
    return readOfficialSource(program, "Main.jack");
  }

  private static byte[] readOfficialSource(String program, String sourceName) throws IOException {
    String resource = RESOURCE_ROOT + program + "/" + sourceName;

    try (InputStream input = OfficialVmGenerationTest.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("Official Project 11 source missing: " + resource);
      }

      return input.readAllBytes();
    }
  }
}
