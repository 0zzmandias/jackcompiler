package jackcompiler.cli;

import jackcompiler.application.SourceProcessingException;
import jackcompiler.application.TokenizeCommand;
import jackcompiler.lexer.LexicalException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Command-line entry point for JackCompiler. */
public final class JackCompiler {

  private JackCompiler() {}

  /** Starts the application with the appropriate process exit status. */
  public static void main(String[] args) {
    int exitCode = run(args, System.out, System.err);

    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  /**
   * Executes an implemented operation and reports its results.
   *
   * @return 0 for success or help, 1 for processing failures, and 2 for invalid arguments
   */
  static int run(String[] args, PrintStream out, PrintStream err) {
    Objects.requireNonNull(out, "out");
    Objects.requireNonNull(err, "err");

    CliArguments arguments;

    try {
      arguments = new CliArgumentParser().parse(args);
    } catch (IllegalArgumentException exception) {
      err.println("Error: " + exception.getMessage());
      return 2;
    }

    if (arguments.command() == CliArguments.Command.HELP) {
      out.println(CliArgumentParser.USAGE);
      return 0;
    }

    try {
      List<Path> outputs =
          new TokenizeCommand()
              .execute(arguments.input().orElseThrow(), arguments.outputDirectory());

      for (Path output : outputs) {
        out.println("Generated: " + output);
      }

      return 0;
    } catch (SourceProcessingException exception) {
      reportProcessingFailure(exception, err);
      return 1;
    }
  }

  private static void reportProcessingFailure(
      SourceProcessingException exception, PrintStream err) {
    Throwable cause = exception.getCause();

    if (cause instanceof LexicalException lexical) {
      err.printf(
          "Lexical error in %s at line %d, column %d: %s%n",
          exception.sourceFile(),
          lexical.position().line(),
          lexical.position().column(),
          lexical.getMessage());
    } else if (cause instanceof IOException) {
      err.printf("I/O error in %s: %s%n", exception.sourceFile(), cause.getMessage());
    } else {
      err.printf("Processing error in %s: %s%n", exception.sourceFile(), cause.getMessage());
    }
  }
}
