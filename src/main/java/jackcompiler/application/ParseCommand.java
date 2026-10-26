package jackcompiler.application;

import jackcompiler.io.JackFileResolver;
import jackcompiler.io.JackSourceReader;
import jackcompiler.io.OutputKind;
import jackcompiler.io.OutputPathResolver;
import jackcompiler.io.TextFileWriter;
import jackcompiler.io.WritePolicy;
import jackcompiler.lexer.JackTokenizer;
import jackcompiler.lexer.LexicalException;
import jackcompiler.parser.JackParser;
import jackcompiler.parser.SyntaxException;
import jackcompiler.xml.ParseTreeXmlWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Coordinates the syntax-analysis use case independently of the command-line interface. */
public final class ParseCommand {

  private final JackFileResolver fileResolver = new JackFileResolver();
  private final JackSourceReader sourceReader = new JackSourceReader();
  private final JackTokenizer tokenizer = new JackTokenizer();
  private final JackParser parser = new JackParser();
  private final ParseTreeXmlWriter xmlWriter = new ParseTreeXmlWriter();
  private final OutputPathResolver outputPathResolver = new OutputPathResolver();
  private final TextFileWriter textFileWriter = new TextFileWriter();

  /**
   * Parses a Jack source or every eligible source directly inside a directory.
   *
   * <p>Sources are processed in deterministic order. Processing stops at the first failure. Outputs
   * already completed for earlier sources remain; later sources are not processed.
   *
   * <p>Default output uses target/generated-parser and may replace existing generated files. Custom
   * output directories use CREATE_NEW and reject collisions. Each output is published only after
   * reading, lexical analysis, syntax analysis and XML serialization have completed.
   *
   * @param input source file or directory
   * @param outputDirectory optional custom output directory
   * @return immutable list of successfully generated output paths
   * @throws NullPointerException if either argument is null
   * @throws SourceProcessingException if discovery, reading, tokenization, parsing, serialization,
   *     destination resolution or writing fails
   */
  public List<Path> execute(Path input, Optional<Path> outputDirectory)
      throws SourceProcessingException {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(outputDirectory, "outputDirectory");

    List<Path> sourceFiles;

    try {
      sourceFiles = fileResolver.resolve(input);
    } catch (IOException | IllegalArgumentException exception) {
      throw new SourceProcessingException(input, exception);
    }

    WritePolicy policy =
        outputDirectory.isPresent() ? WritePolicy.CREATE_NEW : WritePolicy.REPLACE_EXISTING;

    List<Path> outputs = new ArrayList<>();

    for (Path sourceFile : sourceFiles) {
      try {
        String source = sourceReader.read(sourceFile);
        String xml = xmlWriter.toXml(parser.parse(tokenizer.tokenize(source)));

        Path destination =
            outputPathResolver.resolve(sourceFile, outputDirectory, OutputKind.SYNTAX_XML);

        outputs.add(textFileWriter.write(destination, xml, policy));
      } catch (IOException
          | LexicalException
          | SyntaxException
          | IllegalArgumentException exception) {
        throw new SourceProcessingException(sourceFile, exception);
      }
    }

    return List.copyOf(outputs);
  }
}
