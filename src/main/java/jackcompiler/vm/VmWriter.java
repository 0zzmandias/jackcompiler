package jackcompiler.vm;

import java.util.Objects;
import java.util.regex.Pattern;

/** Accumulates valid VM commands without performing file I/O. */
public final class VmWriter {

  private static final Pattern VM_IDENTIFIER = Pattern.compile("[A-Za-z_.$:][A-Za-z0-9_.$:]*");

  private final StringBuilder commands = new StringBuilder();

  /** Writes a push command for the given segment and index. */
  public void writePush(VmSegment segment, int index) {
    validateMemoryAccess(segment, index, false);
    append("push " + segment.vmName() + " " + index);
  }

  /** Writes a pop command for the given segment and index. */
  public void writePop(VmSegment segment, int index) {
    validateMemoryAccess(segment, index, true);
    append("pop " + segment.vmName() + " " + index);
  }

  /** Writes one arithmetic, comparison, or logical VM command. */
  public void writeArithmetic(VmArithmetic operation) {
    Objects.requireNonNull(operation, "operation");
    append(operation.vmName());
  }

  /** Writes a VM label declaration. */
  public void writeLabel(String label) {
    validateIdentifier(label);
    append("label " + label);
  }

  /** Writes an unconditional VM jump. */
  public void writeGoto(String label) {
    validateIdentifier(label);
    append("goto " + label);
  }

  /** Writes a conditional VM jump that consumes the stack's top value. */
  public void writeIf(String label) {
    validateIdentifier(label);
    append("if-goto " + label);
  }

  /** Writes a VM function declaration and its local-variable count. */
  public void writeFunction(String name, int localCount) {
    validateIdentifier(name);
    validateCount(localCount, "local-variable count");
    append("function " + name + " " + localCount);
  }

  /** Writes a VM function call and its argument count. */
  public void writeCall(String name, int argumentCount) {
    validateIdentifier(name);
    validateCount(argumentCount, "argument count");
    append("call " + name + " " + argumentCount);
  }

  /** Writes a VM return command. */
  public void writeReturn() {
    append("return");
  }

  /** Returns the accumulated VM text, including its final newline when nonempty. */
  public String toVm() {
    return commands.toString();
  }

  private static void validateIdentifier(String identifier) {
    Objects.requireNonNull(identifier, "identifier");

    if (!VM_IDENTIFIER.matcher(identifier).matches()) {
      throw new IllegalArgumentException("Invalid VM identifier: " + identifier);
    }
  }

  private static void validateCount(int count, String description) {
    if (count < 0) {
      throw new IllegalArgumentException("VM " + description + " must be non-negative.");
    }
  }

  private static void validateMemoryAccess(VmSegment segment, int index, boolean pop) {
    Objects.requireNonNull(segment, "segment");

    if (index < 0) {
      throw new IllegalArgumentException("VM segment index must be non-negative.");
    }

    switch (segment) {
      case CONSTANT -> {
        if (pop) {
          throw new IllegalArgumentException("Cannot pop to the constant segment.");
        }
        if (index > 32767) {
          throw new IllegalArgumentException("VM constant must be between 0 and 32767.");
        }
      }
      case POINTER -> {
        if (index > 1) {
          throw new IllegalArgumentException("Pointer index must be 0 or 1.");
        }
      }
      case TEMP -> {
        if (index > 7) {
          throw new IllegalArgumentException("Temp index must be between 0 and 7.");
        }
      }
      default -> {
        // Other segments accept non-negative indices.
      }
    }
  }

  private void append(String command) {
    commands.append(command).append('\n');
  }
}
