package jackcompiler.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Resolves and writes nand2tetris token XML files beside their Jack sources. */
public final class TokenXmlFiles {

  private static final String JACK_EXTENSION = ".jack";
  private static final String TOKEN_XML_SUFFIX = "T.xml";

  /**
   * Derives the XML output path from a Jack source path.
   *
   * <p>For example, Main.jack becomes MainT.xml in the same directory.
   *
   * @param sourceFile path to a Jack source file
   * @return absolute, normalized output path
   */
  public Path outputPath(Path sourceFile) {
    Objects.requireNonNull(sourceFile, "sourceFile");

    Path normalized = sourceFile.toAbsolutePath().normalize();
    Path fileName = normalized.getFileName();

    if (fileName == null) {
      throw new IllegalArgumentException("Expected a Jack source file path.");
    }

    String name = fileName.toString();

    if (!name.endsWith(JACK_EXTENSION) || name.length() == JACK_EXTENSION.length()) {
      throw new IllegalArgumentException(
          "Source file must have a nonempty name and the .jack extension: " + normalized);
    }

    String baseName = name.substring(0, name.length() - JACK_EXTENSION.length());

    return normalized.resolveSibling(baseName + TOKEN_XML_SUFFIX);
  }

  /**
   * Writes token XML as UTF-8 beside its corresponding Jack source.
   *
   * <p>An existing output file is replaced. The source file is never modified.
   *
   * @param sourceFile corresponding Jack source path
   * @param xml serialized token XML
   * @return path of the generated XML file
   * @throws IOException if the XML cannot be written
   */
  public Path write(Path sourceFile, String xml) throws IOException {
    Objects.requireNonNull(xml, "xml");

    Path destination = outputPath(sourceFile);
    Files.writeString(destination, xml, StandardCharsets.UTF_8);

    return destination;
  }
}
