package jackcompiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BuildEnvironmentTest {

  @Test
  void runsOnSupportedJavaVersion() {
    assertTrue(Runtime.version().feature() >= 21);
  }
}
