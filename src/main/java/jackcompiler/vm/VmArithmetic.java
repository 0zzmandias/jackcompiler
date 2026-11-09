package jackcompiler.vm;

/** Arithmetic, comparison, and logical commands supported by the VM language. */
public enum VmArithmetic {
  ADD("add"),
  SUB("sub"),
  NEG("neg"),
  EQ("eq"),
  GT("gt"),
  LT("lt"),
  AND("and"),
  OR("or"),
  NOT("not");

  private final String vmName;

  VmArithmetic(String vmName) {
    this.vmName = vmName;
  }

  /** Returns the operation name used in VM commands. */
  public String vmName() {
    return vmName;
  }
}
