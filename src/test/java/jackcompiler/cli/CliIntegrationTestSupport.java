package jackcompiler.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared source-file and CLI execution utilities for integration tests. */
final class CliIntegrationTestSupport {

  private CliIntegrationTestSupport() {}

  static Path write(Path directory, String name, String source) throws IOException {
    return Files.writeString(directory.resolve(name), source, StandardCharsets.UTF_8);
  }

  static Execution execute(String... arguments) {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
      int status = JackCompiler.run(arguments, out, err);

      return new Execution(
          status, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }
  }

  record Execution(int status, String stdout, String stderr) {}
}
