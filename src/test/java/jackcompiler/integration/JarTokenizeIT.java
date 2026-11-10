package jackcompiler.integration;

import static jackcompiler.integration.JarExecutionTestSupport.normalizeLineEndings;
import static jackcompiler.integration.JarExecutionTestSupport.resource;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.integration.JarExecutionTestSupport.Execution;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Tests tokenization through the packaged executable JAR in a separate JVM. */
class JarTokenizeIT {

  @TempDir Path tempDir;

  @Test
  void packagedJarMatchesOfficialSquareReference() throws Exception {
    Path source = tempDir.resolve("Main.jack");
    byte[] original = resource("Square", "Main.jack");
    Files.write(source, original);
    Path outputDirectory = tempDir.resolve("output");

    Execution result =
        execute(tempDir, "tokenize", source.toString(), "--output-dir", outputDirectory.toString());

    Path output = outputDirectory.resolve("MainT.xml");
    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals("Generated: " + output + System.lineSeparator(), result.stdout());
    assertEquals(
        normalizeLineEndings(new String(resource("Square", "MainT.xml"), StandardCharsets.UTF_8)),
        Files.readString(output, StandardCharsets.UTF_8));
    assertArrayEquals(original, Files.readAllBytes(source));
  }

  @Test
  void packagedJarUsesDefaultDirectoryRelativeToItsWorkingDirectory() throws Exception {
    Path workingDirectory = Files.createDirectory(tempDir.resolve("working"));
    Files.writeString(workingDirectory.resolve("Main.jack"), "class Main {}\n");

    Execution result = execute(workingDirectory, "tokenize", "Main.jack");

    Path expected = workingDirectory.resolve("target/generated-tokens/MainT.xml");
    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals("Generated: " + expected + System.lineSeparator(), result.stdout());
    assertTrue(Files.readString(expected).contains("<identifier> Main </identifier>"));
  }

  @Test
  void packagedJarSortsDirectoryInputsWithoutRecursion() throws Exception {
    Path sources = Files.createDirectory(tempDir.resolve("sources"));
    Files.writeString(sources.resolve("Zebra.jack"), "class Zebra {}");
    Files.writeString(sources.resolve("Alpha.jack"), "class Alpha {}");
    Path nested = Files.createDirectory(sources.resolve("nested"));
    Files.writeString(nested.resolve("Nested.jack"), "class Nested {}");
    Path destination = tempDir.resolve("out");

    Execution result =
        execute(tempDir, "tokenize", sources.toString(), "--output-dir", destination.toString());

    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals(
        "Generated: "
            + destination.resolve("AlphaT.xml")
            + System.lineSeparator()
            + "Generated: "
            + destination.resolve("ZebraT.xml")
            + System.lineSeparator(),
        result.stdout());
    assertFalse(Files.exists(destination.resolve("NestedT.xml")));
  }

  @Test
  void packagedJarReportsLexicalErrorWithSourcePosition() throws Exception {
    Path source = Files.writeString(tempDir.resolve("Invalid.jack"), "class Main {}\n@");
    Path destination = tempDir.resolve("out");

    Execution result =
        execute(tempDir, "tokenize", source.toString(), "--output-dir", destination.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains(source.toString()));
    assertTrue(result.stderr().contains("line 2, column 1"));
    assertFalse(Files.exists(destination.resolve("InvalidT.xml")));
  }

  @Test
  void packagedJarRejectsMalformedUtf8() throws Exception {
    Path source = tempDir.resolve("Malformed.jack");
    byte[] invalid = {(byte) 0xC3, (byte) 0x28};
    Files.write(source, invalid);

    Execution result =
        execute(
            tempDir,
            "tokenize",
            source.toString(),
            "--output-dir",
            tempDir.resolve("out").toString());

    assertEquals(1, result.exitCode());
    assertTrue(result.stderr().contains("I/O error"));
    assertTrue(result.stderr().contains("Malformed.jack"));
    assertFalse(result.stderr().contains("line 1, column"));
    assertArrayEquals(invalid, Files.readAllBytes(source));
  }

  @Test
  void packagedJarRejectsCustomOutputCollisionWithoutOverwriting() throws Exception {
    Path source = Files.writeString(tempDir.resolve("Main.jack"), "class Main {}");
    Path outputDirectory = Files.createDirectory(tempDir.resolve("out"));
    Path output = Files.writeString(outputDirectory.resolve("MainT.xml"), "original");

    Execution result =
        execute(tempDir, "tokenize", source.toString(), "--output-dir", outputDirectory.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(result.stderr().contains("I/O error"));
    assertEquals("original", Files.readString(output));
    assertEquals("class Main {}", Files.readString(source));
  }

  @Test
  void packagedJarStopsAtFirstSourceFailure() throws Exception {
    Path sources = Files.createDirectory(tempDir.resolve("sources"));
    Files.writeString(sources.resolve("A.jack"), "class A {}");
    Files.writeString(sources.resolve("B.jack"), "@");
    Files.writeString(sources.resolve("C.jack"), "class C {}");
    Path destination = tempDir.resolve("out");

    Execution result =
        execute(tempDir, "tokenize", sources.toString(), "--output-dir", destination.toString());

    assertEquals(1, result.exitCode());
    assertEquals("", result.stdout());
    assertTrue(Files.isRegularFile(destination.resolve("AT.xml")));
    assertFalse(Files.exists(destination.resolve("BT.xml")));
    assertFalse(Files.exists(destination.resolve("CT.xml")));
  }

  @ParameterizedTest(name = "official tokens: {0}/{1}")
  @MethodSource("officialPrograms")
  void packagedJarMatchesOfficialTokenXmlForAllPrograms(String suite, String name)
      throws Exception {
    Path source = tempDir.resolve(name + ".jack");
    byte[] original = resource(suite, name + ".jack");
    Files.write(source, original);

    Path directory = tempDir.resolve("token-output");
    Path output = directory.resolve(name + "T.xml");

    Execution result =
        execute(tempDir, "tokenize", source.toString(), "--output-dir", directory.toString());

    assertEquals(0, result.exitCode());
    assertEquals("", result.stderr());
    assertEquals("Generated: " + output + System.lineSeparator(), result.stdout());
    assertEquals(
        normalizeLineEndings(new String(resource(suite, name + "T.xml"), StandardCharsets.UTF_8)),
        Files.readString(output, StandardCharsets.UTF_8));
    assertArrayEquals(original, Files.readAllBytes(source));
    assertFalse(Files.exists(directory.resolve(name + "P.xml")));
  }

  private static Stream<Arguments> officialPrograms() {
    return JarExecutionTestSupport.officialPrograms();
  }

  private Execution execute(Path workingDirectory, String... arguments)
      throws IOException, InterruptedException {
    return JarExecutionTestSupport.execute(tempDir, workingDirectory, arguments);
  }
}
