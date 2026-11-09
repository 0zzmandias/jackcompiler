package jackcompiler.vm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VmWriterTest {

  @Test
  void definesAllEightMemorySegments() {
    assertArrayEquals(
        new VmSegment[] {
          VmSegment.CONSTANT,
          VmSegment.LOCAL,
          VmSegment.ARGUMENT,
          VmSegment.STATIC,
          VmSegment.THIS,
          VmSegment.THAT,
          VmSegment.POINTER,
          VmSegment.TEMP
        },
        VmSegment.values());
  }

  @Test
  void definesAllNineArithmeticCommands() {
    assertArrayEquals(
        new VmArithmetic[] {
          VmArithmetic.ADD,
          VmArithmetic.SUB,
          VmArithmetic.NEG,
          VmArithmetic.EQ,
          VmArithmetic.GT,
          VmArithmetic.LT,
          VmArithmetic.AND,
          VmArithmetic.OR,
          VmArithmetic.NOT
        },
        VmArithmetic.values());
  }

  @Test
  void producesEmptyOutputBeforeAnyCommands() {
    VmWriter writer = new VmWriter();

    assertEquals("", writer.toVm());
  }

  @Test
  void writesAllEightPushSegments() {
    VmWriter writer = new VmWriter();

    for (VmSegment segment : VmSegment.values()) {
      writer.writePush(segment, 0);
    }

    assertEquals(
        "push constant 0\n"
            + "push local 0\n"
            + "push argument 0\n"
            + "push static 0\n"
            + "push this 0\n"
            + "push that 0\n"
            + "push pointer 0\n"
            + "push temp 0\n",
        writer.toVm());
  }

  @Test
  void writesAllSevenWritableSegments() {
    VmWriter writer = new VmWriter();

    for (VmSegment segment : VmSegment.values()) {
      if (segment != VmSegment.CONSTANT) {
        writer.writePop(segment, 0);
      }
    }

    assertEquals(
        "pop local 0\n"
            + "pop argument 0\n"
            + "pop static 0\n"
            + "pop this 0\n"
            + "pop that 0\n"
            + "pop pointer 0\n"
            + "pop temp 0\n",
        writer.toVm());
  }

  @Test
  void writesAllNineArithmeticCommands() {
    VmWriter writer = new VmWriter();

    for (VmArithmetic operation : VmArithmetic.values()) {
      writer.writeArithmetic(operation);
    }

    assertEquals("add\nsub\nneg\neq\ngt\nlt\nand\nor\nnot\n", writer.toVm());
  }

  @Test
  void preservesCommandOrderAndFormatting() {
    VmWriter writer = new VmWriter();

    writer.writePush(VmSegment.CONSTANT, 5);
    writer.writePush(VmSegment.CONSTANT, 3);
    writer.writeArithmetic(VmArithmetic.SUB);
    writer.writePop(VmSegment.LOCAL, 2);

    assertEquals(
        "push constant 5\n" + "push constant 3\n" + "sub\n" + "pop local 2\n", writer.toVm());
  }

  @Test
  void rejectsPopToConstantWithoutChangingOutput() {
    VmWriter writer = new VmWriter();
    writer.writePush(VmSegment.CONSTANT, 7);

    assertThrows(IllegalArgumentException.class, () -> writer.writePop(VmSegment.CONSTANT, 0));

    assertEquals("push constant 7\n", writer.toVm());
  }

  @Test
  void rejectsNegativeIndicesWithoutChangingOutput() {
    VmWriter writer = new VmWriter();

    assertThrows(IllegalArgumentException.class, () -> writer.writePush(VmSegment.LOCAL, -1));
    assertThrows(IllegalArgumentException.class, () -> writer.writePop(VmSegment.ARGUMENT, -1));

    assertEquals("", writer.toVm());
  }

  @Test
  void enforcesPointerIndexBounds() {
    VmWriter writer = new VmWriter();

    writer.writePush(VmSegment.POINTER, 1);
    writer.writePop(VmSegment.POINTER, 0);

    assertThrows(IllegalArgumentException.class, () -> writer.writePush(VmSegment.POINTER, 2));
    assertThrows(IllegalArgumentException.class, () -> writer.writePop(VmSegment.POINTER, 2));

    assertEquals("push pointer 1\npop pointer 0\n", writer.toVm());
  }

  @Test
  void enforcesTempIndexBounds() {
    VmWriter writer = new VmWriter();

    writer.writePush(VmSegment.TEMP, 7);
    writer.writePop(VmSegment.TEMP, 0);

    assertThrows(IllegalArgumentException.class, () -> writer.writePush(VmSegment.TEMP, 8));
    assertThrows(IllegalArgumentException.class, () -> writer.writePop(VmSegment.TEMP, 8));

    assertEquals("push temp 7\npop temp 0\n", writer.toVm());
  }

  @Test
  void enforcesConstantValueBounds() {
    VmWriter writer = new VmWriter();

    writer.writePush(VmSegment.CONSTANT, 0);
    writer.writePush(VmSegment.CONSTANT, 32767);

    assertThrows(IllegalArgumentException.class, () -> writer.writePush(VmSegment.CONSTANT, 32768));

    assertEquals("push constant 0\npush constant 32767\n", writer.toVm());
  }

  @Test
  void rejectsNullOperandsWithoutChangingOutput() {
    VmWriter writer = new VmWriter();

    assertThrows(NullPointerException.class, () -> writer.writePush(null, 0));
    assertThrows(NullPointerException.class, () -> writer.writePop(null, 0));
    assertThrows(NullPointerException.class, () -> writer.writeArithmetic(null));

    assertEquals("", writer.toVm());
  }

  @Test
  void keepsWriterInstancesIndependent() {
    VmWriter first = new VmWriter();
    VmWriter second = new VmWriter();

    first.writeArithmetic(VmArithmetic.ADD);

    assertEquals("add\n", first.toVm());
    assertEquals("", second.toVm());

    second.writeArithmetic(VmArithmetic.NOT);

    assertEquals("add\n", first.toVm());
    assertEquals("not\n", second.toVm());
  }

  @Test
  void preservesPreviouslyReturnedSnapshots() {
    VmWriter writer = new VmWriter();

    writer.writePush(VmSegment.CONSTANT, 1);
    String snapshot = writer.toVm();

    writer.writeArithmetic(VmArithmetic.NEG);

    assertEquals("push constant 1\n", snapshot);
    assertEquals("push constant 1\nneg\n", writer.toVm());
  }

  @Test
  void writesLabelGotoAndConditionalGoto() {
    VmWriter writer = new VmWriter();

    writer.writeLabel("LOOP_START");
    writer.writeGoto("LOOP_END");
    writer.writeIf("LOOP_START");

    assertEquals("label LOOP_START\ngoto LOOP_END\nif-goto LOOP_START\n", writer.toVm());
  }

  @Test
  void writesFunctionCallAndReturn() {
    VmWriter writer = new VmWriter();

    writer.writeFunction("Main.main", 3);
    writer.writeCall("Output.printInt", 1);
    writer.writeReturn();

    assertEquals("function Main.main 3\ncall Output.printInt 1\nreturn\n", writer.toVm());
  }

  @Test
  void preservesOrderAcrossAllCommandFamilies() {
    VmWriter writer = new VmWriter();

    writer.writeFunction("Main.main", 1);
    writer.writePush(VmSegment.CONSTANT, 2);
    writer.writePop(VmSegment.LOCAL, 0);
    writer.writeLabel("LOOP");
    writer.writePush(VmSegment.LOCAL, 0);
    writer.writeArithmetic(VmArithmetic.NEG);
    writer.writeIf("END");
    writer.writeGoto("LOOP");
    writer.writeLabel("END");
    writer.writeCall("Output.printInt", 1);
    writer.writeReturn();

    assertEquals(
        "function Main.main 1\n"
            + "push constant 2\n"
            + "pop local 0\n"
            + "label LOOP\n"
            + "push local 0\n"
            + "neg\n"
            + "if-goto END\n"
            + "goto LOOP\n"
            + "label END\n"
            + "call Output.printInt 1\n"
            + "return\n",
        writer.toVm());
  }

  @Test
  void acceptsVMIdentifierSymbolsAndDigitsAfterFirstCharacter() {
    VmWriter writer = new VmWriter();

    writer.writeLabel("_LOOP.2$inner:next");
    writer.writeGoto("$DONE");
    writer.writeIf(":EXIT");
    writer.writeFunction("Main.main_1", 0);
    writer.writeCall("Screen.draw$helper:2", 1);

    assertEquals(
        "label _LOOP.2$inner:next\n"
            + "goto $DONE\n"
            + "if-goto :EXIT\n"
            + "function Main.main_1 0\n"
            + "call Screen.draw$helper:2 1\n",
        writer.toVm());
  }

  @Test
  void acceptsUnqualifiedFunctionNames() {
    VmWriter writer = new VmWriter();

    writer.writeFunction("start", 0);
    writer.writeCall("helper", 0);

    assertEquals("function start 0\ncall helper 0\n", writer.toVm());
  }

  @Test
  void acceptsZeroCountsAndRepeatedReturns() {
    VmWriter writer = new VmWriter();

    writer.writeFunction("Main.empty", 0);
    writer.writeCall("Main.empty", 0);
    writer.writeReturn();
    writer.writeReturn();

    assertEquals("function Main.empty 0\ncall Main.empty 0\nreturn\nreturn\n", writer.toVm());
  }

  @Test
  void rejectsNegativeLocalVariableCountsWithoutChangingOutput() {
    VmWriter writer = new VmWriter();
    writer.writeLabel("OK");

    assertThrows(IllegalArgumentException.class, () -> writer.writeFunction("Main.main", -1));
    assertThrows(
        IllegalArgumentException.class, () -> writer.writeFunction("Main.main", Integer.MIN_VALUE));

    assertEquals("label OK\n", writer.toVm());
  }

  @Test
  void rejectsNegativeArgumentCountsWithoutChangingOutput() {
    VmWriter writer = new VmWriter();
    writer.writeLabel("OK");

    assertThrows(IllegalArgumentException.class, () -> writer.writeCall("Main.main", -1));
    assertThrows(
        IllegalArgumentException.class, () -> writer.writeCall("Main.main", Integer.MIN_VALUE));

    assertEquals("label OK\n", writer.toVm());
  }

  @Test
  void rejectsNullLabelNamesWithoutChangingOutput() {
    VmWriter writer = new VmWriter();
    writer.writeReturn();

    assertThrows(NullPointerException.class, () -> writer.writeLabel(null));
    assertThrows(NullPointerException.class, () -> writer.writeGoto(null));
    assertThrows(NullPointerException.class, () -> writer.writeIf(null));

    assertEquals("return\n", writer.toVm());
  }

  @Test
  void rejectsNullFunctionAndCallNamesWithoutChangingOutput() {
    VmWriter writer = new VmWriter();
    writer.writeReturn();

    assertThrows(NullPointerException.class, () -> writer.writeFunction(null, 0));
    assertThrows(NullPointerException.class, () -> writer.writeCall(null, 0));

    assertEquals("return\n", writer.toVm());
  }

  @Test
  void rejectsBlankNamesWithoutChangingOutput() {
    VmWriter writer = new VmWriter();
    writer.writeReturn();

    for (String invalid : new String[] {"", " ", "\t", "  \t  "}) {
      assertThrows(IllegalArgumentException.class, () -> writer.writeLabel(invalid));
      assertThrows(IllegalArgumentException.class, () -> writer.writeGoto(invalid));
      assertThrows(IllegalArgumentException.class, () -> writer.writeIf(invalid));
      assertThrows(IllegalArgumentException.class, () -> writer.writeFunction(invalid, 0));
      assertThrows(IllegalArgumentException.class, () -> writer.writeCall(invalid, 0));
    }

    assertEquals("return\n", writer.toVm());
  }

  @Test
  void rejectsIdentifiersStartingWithDigitsWithoutChangingOutput() {
    VmWriter writer = new VmWriter();
    writer.writeReturn();

    assertThrows(IllegalArgumentException.class, () -> writer.writeLabel("1LOOP"));
    assertThrows(IllegalArgumentException.class, () -> writer.writeGoto("2LOOP"));
    assertThrows(IllegalArgumentException.class, () -> writer.writeIf("3LOOP"));
    assertThrows(IllegalArgumentException.class, () -> writer.writeFunction("4Main", 0));
    assertThrows(IllegalArgumentException.class, () -> writer.writeCall("5Main", 0));

    assertEquals("return\n", writer.toVm());
  }

  @Test
  void rejectsInvalidPunctuationWhitespaceAndLineBreaksWithoutChangingOutput() {
    VmWriter writer = new VmWriter();
    writer.writeReturn();

    for (String invalid :
        new String[] {"has space", "bad-name", "bad/name", "bad\nreturn", "bad\tname"}) {
      assertThrows(IllegalArgumentException.class, () -> writer.writeLabel(invalid));
      assertThrows(IllegalArgumentException.class, () -> writer.writeGoto(invalid));
      assertThrows(IllegalArgumentException.class, () -> writer.writeIf(invalid));
      assertThrows(IllegalArgumentException.class, () -> writer.writeFunction(invalid, 0));
      assertThrows(IllegalArgumentException.class, () -> writer.writeCall(invalid, 0));
    }

    assertEquals("return\n", writer.toVm());
  }
}
