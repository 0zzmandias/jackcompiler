package jackcompiler.vm;

import java.util.Objects;

/** Accumulates valid VM commands without performing file I/O. */
public final class VmWriter {

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

  /** Returns the accumulated VM text, including its final newline when nonempty. */
  public String toVm() {
    return commands.toString();
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
