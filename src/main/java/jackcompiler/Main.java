package jackcompiler;

import jackcompiler.cli.CommandLineArguments;
import jackcompiler.cli.TokenizeCommand;
import jackcompiler.lexer.LexicalException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Command-line entry point for the Jack compiler. */
public final class Main {

  private Main() {}

  /** Starts the application and reports a nonzero exit status on failure. */
  public static void main(String[] args) {
    int exitCode = run(args, System.out, System.err);

    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  /**
   * Executes a command and writes results to the supplied output streams.
   *
   * @return 0 for success, 1 for processing errors, or 2 for command errors
   */
  static int run(String[] args, PrintStream out, PrintStream err) {
    Objects.requireNonNull(out, "out");
    Objects.requireNonNull(err, "err");

    CommandLineArguments arguments;

    try {
      arguments = CommandLineArguments.parse(args);
    } catch (IllegalArgumentException exception) {
      err.println("Error: " + exception.getMessage());
      return 2;
    }

    if (arguments.mode() == CommandLineArguments.Mode.PARSE) {
      err.println("Error: parse is not implemented yet (Part 2).");
      return 2;
    }

    try {
      List<Path> outputs = new TokenizeCommand().execute(arguments.input());

      for (Path output : outputs) {
        out.println("Generated: " + output);
      }

      return 0;
    } catch (LexicalException exception) {
      err.printf(
          "Lexical error at line %d, column %d: %s%n",
          exception.position().line(), exception.position().column(), exception.getMessage());
      return 1;
    } catch (IOException exception) {
      err.println("I/O error: " + exception.getMessage());
      return 1;
    } catch (IllegalArgumentException exception) {
      err.println("Error: " + exception.getMessage());
      return 1;
    }
  }
}
