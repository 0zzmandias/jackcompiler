package jackcompiler.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/** Shared process execution and reference utilities for executable-JAR integration tests. */
final class JarExecutionTestSupport {

  static final Path JAR = Path.of("target", "jackcompiler.jar").toAbsolutePath().normalize();

  private JarExecutionTestSupport() {}

  static Execution execute(Path tempDir, Path workingDirectory, String... arguments)
      throws IOException, InterruptedException {
    Path stdout = Files.createTempFile(tempDir, "jar-stdout-", ".txt");
    Path stderr = Files.createTempFile(tempDir, "jar-stderr-", ".txt");

    List<String> command = new ArrayList<>();
    command.add(javaExecutable().toString());
    command.add("-jar");
    command.add(JAR.toString());
    command.addAll(List.of(arguments));

    Process process =
        new ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .redirectOutput(stdout.toFile())
            .redirectError(stderr.toFile())
            .start();

    try {
      assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Packaged JAR process timed out");

      return new Execution(
          process.exitValue(),
          Files.readString(stdout, StandardCharsets.UTF_8),
          Files.readString(stderr, StandardCharsets.UTF_8));
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  static Stream<Arguments> officialPrograms() {
    return Stream.of(
        Arguments.of("Square", "Main"),
        Arguments.of("Square", "Square"),
        Arguments.of("Square", "SquareGame"),
        Arguments.of("ExpressionLessSquare", "Main"),
        Arguments.of("ExpressionLessSquare", "Square"),
        Arguments.of("ExpressionLessSquare", "SquareGame"),
        Arguments.of("ArrayTest", "Main"));
  }

  static byte[] resource(String suite, String name) throws IOException {
    String path = "/nand2tetris/" + suite + "/" + name;

    try (InputStream stream = JarExecutionTestSupport.class.getResourceAsStream(path)) {
      if (stream == null) {
        throw new IOException("Official reference missing: " + path);
      }
      return stream.readAllBytes();
    }
  }

  static String normalizeLineEndings(String text) {
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  static void assertNoTemporaryFiles(Path directory) throws IOException {
    try (Stream<Path> entries = Files.list(directory)) {
      assertEquals(
          0L,
          entries
              .filter(path -> path.getFileName().toString().startsWith(".jackcompiler-"))
              .count());
    }
  }

  private static Path javaExecutable() {
    String name =
        System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
            ? "java.exe"
            : "java";

    return Path.of(System.getProperty("java.home"), "bin", name);
  }

  record Execution(int exitCode, String stdout, String stderr) {}
}
