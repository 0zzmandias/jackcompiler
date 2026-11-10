package jackcompiler.application;

import jackcompiler.compiler.CompilationException;
import jackcompiler.compiler.JackVmCompiler;
import jackcompiler.io.JackFileResolver;
import jackcompiler.io.JackSourceReader;
import jackcompiler.io.OutputPathResolver;
import jackcompiler.io.TextFileWriter;
import jackcompiler.io.WritePolicy;
import jackcompiler.lexer.JackTokenizer;
import jackcompiler.lexer.LexicalException;
import jackcompiler.parser.JackParser;
import jackcompiler.parser.SyntaxException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Coordinates recursive Jack-to-VM compilation without depending on the command-line interface. */
public final class CompileCommand {

  private final JackFileResolver fileResolver = new JackFileResolver();
  private final JackSourceReader sourceReader = new JackSourceReader();
  private final JackTokenizer tokenizer = new JackTokenizer();
  private final JackParser parser = new JackParser();
  private final JackVmCompiler compiler = new JackVmCompiler();
  private final OutputPathResolver outputPathResolver = new OutputPathResolver();
  private final TextFileWriter textFileWriter = new TextFileWriter();

  /**
   * Compiles one Jack file or a directory tree into VM source files.
   *
   * <p>All sources and output destinations are determined before compilation begins. Sources are
   * processed sequentially, in deterministic relative-path order. The first failure stops the
   * batch: previously published outputs remain unchanged and subsequent sources are not processed.
   * No global rollback is attempted.
   *
   * <p>By default, each .vm file is written next to its .jack source and may replace an existing
   * regular VM output. An explicit output directory preserves relative subdirectories and uses
   * CREATE_NEW, refusing occupied destinations. Each VM file is published only after its complete
   * lexical, syntactic and semantic compilation succeeds.
   *
   * @param input Jack file or directory to compile recursively
   * @param outputDirectory optional destination root
   * @return immutable list of successfully published absolute, normalized VM paths
   * @throws NullPointerException if an argument is null
   * @throws SourceProcessingException if discovery, destination planning, reading, compilation or
   *     output publication fails
   */
  public List<Path> execute(Path input, Optional<Path> outputDirectory)
      throws SourceProcessingException {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(outputDirectory, "outputDirectory");

    Map<Path, Path> destinations;
    try {
      List<Path> sources = fileResolver.resolveRecursively(input);
      destinations = outputPathResolver.resolveVmBatch(input, sources, outputDirectory);
    } catch (IOException | IllegalArgumentException exception) {
      // No source has been selected for processing: report the requested input.
      throw new SourceProcessingException(input, exception);
    }

    WritePolicy policy = outputPathResolver.writePolicy(outputDirectory);
    List<Path> outputs = new ArrayList<>();

    for (Map.Entry<Path, Path> entry : destinations.entrySet()) {
      Path sourceFile = entry.getKey();
      try {
        String source = sourceReader.read(sourceFile);
        String vm = compiler.compile(parser.parse(tokenizer.tokenize(source)));
        outputs.add(textFileWriter.write(entry.getValue(), vm, policy));
      } catch (IOException
          | LexicalException
          | SyntaxException
          | CompilationException
          | IllegalArgumentException exception) {
        throw new SourceProcessingException(sourceFile, exception);
      }
    }

    return List.copyOf(outputs);
  }
}
