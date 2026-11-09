package jackcompiler.vm;

/** Memory segments supported by the nand2tetris VM language. */
public enum VmSegment {
  CONSTANT("constant"),
  LOCAL("local"),
  ARGUMENT("argument"),
  STATIC("static"),
  THIS("this"),
  THAT("that"),
  POINTER("pointer"),
  TEMP("temp");

  private final String vmName;

  VmSegment(String vmName) {
    this.vmName = vmName;
  }

  /** Returns the segment name used in VM commands. */
  public String vmName() {
    return vmName;
  }
}
