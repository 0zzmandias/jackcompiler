package jackcompiler.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jackcompiler.lexer.LexicalException;
import jackcompiler.token.SourcePosition;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SourceProcessingExceptionTest {

  @Test
  void retainsOriginalIoCauseAndSourcePath() {
    Path source = Path.of("examples", "Main.jack");
    IOException cause = new IOException("Cannot read source");

    SourceProcessingException exception = new SourceProcessingException(source, cause);

    assertSame(cause, exception.getCause());
    assertEquals(source.toAbsolutePath().normalize(), exception.sourceFile());
    assertTrue(exception.getMessage().contains("Main.jack"));
    assertTrue(exception.getMessage().contains("Cannot read source"));
  }

  @Test
  void retainsLexicalCauseAndPosition() {
    SourcePosition position = new SourcePosition(3, 7);
    LexicalException cause = new LexicalException("Unexpected character", position);

    SourceProcessingException exception =
        new SourceProcessingException(Path.of("Invalid.jack"), cause);

    LexicalException original = assertInstanceOf(LexicalException.class, exception.getCause());

    assertSame(cause, original);
    assertEquals(position, original.position());
    assertTrue(exception.getMessage().contains("Invalid.jack"));
  }

  @Test
  void retainsSerializationFailureCause() {
    IllegalArgumentException cause = new IllegalArgumentException("Invalid XML character U+0001");

    SourceProcessingException exception =
        new SourceProcessingException(Path.of("Main.jack"), cause);

    assertSame(cause, exception.getCause());
    assertTrue(exception.getMessage().contains("Invalid XML character"));
  }

  @Test
  void normalizesRelativeSourcePath() {
    SourceProcessingException exception =
        new SourceProcessingException(
            Path.of("examples", "..", "Main.jack"), new IOException("Error"));

    assertEquals(Path.of("Main.jack").toAbsolutePath().normalize(), exception.sourceFile());
  }

  @Test
  void rejectsNullSource() {
    assertThrows(
        NullPointerException.class,
        () -> new SourceProcessingException(null, new IOException("Error")));
  }

  @Test
  void rejectsNullCause() {
    assertThrows(
        NullPointerException.class,
        () -> new SourceProcessingException(Path.of("Main.jack"), null));
  }
}
