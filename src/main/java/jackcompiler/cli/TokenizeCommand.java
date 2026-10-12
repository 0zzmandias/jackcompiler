package jackcompiler.cli;

import jackcompiler.io.JackSourceFiles;
import jackcompiler.io.TokenXmlFiles;
import jackcompiler.lexer.JackTokenizer;
import jackcompiler.xml.TokenXmlWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes the complete Jack tokenization and XML output workflow. */
public final class TokenizeCommand {

  private final JackSourceFiles sourceFiles = new JackSourceFiles();
  private final JackTokenizer tokenizer = new JackTokenizer();
  private final TokenXmlWriter xmlWriter = new TokenXmlWriter();
  private final TokenXmlFiles xmlFiles = new TokenXmlFiles();

  /**
   * Tokenizes one Jack file or every Jack file directly inside a directory.
   *
   * <p>Sources are processed in deterministic order. Processing stops on the first error; outputs
   * from previously completed sources may remain.
   *
   * @param input source file or directory
   * @return immutable list of generated XML paths
   * @throws IOException if a source cannot be read or an XML file cannot be written
   */
  public List<Path> execute(Path input) throws IOException {
    Objects.requireNonNull(input, "input");

    List<Path> outputs = new ArrayList<>();

    for (Path sourceFile : sourceFiles.resolve(input)) {
      String source = sourceFiles.read(sourceFile);
      String xml = xmlWriter.toXml(tokenizer.tokenize(source));

      outputs.add(xmlFiles.write(sourceFile, xml));
    }

    return List.copyOf(outputs);
  }
}
