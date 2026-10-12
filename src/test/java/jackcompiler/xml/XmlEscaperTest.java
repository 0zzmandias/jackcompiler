package jackcompiler.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class XmlEscaperTest {

  @Test
  void escapesAllFiveXmlReservedCharacters() {
    assertEquals("&amp;&lt;&gt;&quot;&apos;", XmlEscaper.escape("&<>\"'"));
  }

  @Test
  void doesNotTreatExistingEntityTextAsAlreadyEscaped() {
    assertEquals("&amp;lt; &amp;amp; &amp;quot;", XmlEscaper.escape("&lt; &amp; &quot;"));
  }

  @Test
  void preservesWhitespaceUnicodeAndSupplementaryCharacters() {
    String value = " Olá\t\n\r😀 ";

    assertEquals(value, XmlEscaper.escape(value));
  }

  @Test
  void preservesCommentMarkersAsOrdinaryText() {
    String value = "// comment /* block */";

    assertEquals(value, XmlEscaper.escape(value));
  }

  @Test
  void acceptsEmptyString() {
    assertEquals("", XmlEscaper.escape(""));
  }

  @Test
  void rejectsNullInput() {
    assertThrows(NullPointerException.class, () -> XmlEscaper.escape(null));
  }

  @ParameterizedTest
  @ValueSource(ints = {0x9, 0xA, 0xD, 0x20, 0xD7FF, 0xE000, 0xFFFD, 0x10000, 0x10FFFF})
  void acceptsXmlCharacterBoundaryValues(int codePoint) {
    String value = Character.toString(codePoint);

    assertEquals(value, XmlEscaper.escape(value));
  }

  @ParameterizedTest
  @ValueSource(
      ints = {0x0, 0x1, 0x8, 0xB, 0xC, 0x1F, 0xD800, 0xDBFF, 0xDC00, 0xDFFF, 0xFFFE, 0xFFFF})
  void rejectsCharactersForbiddenByXml(int codePoint) {
    String value = Character.toString(codePoint);

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> XmlEscaper.escape(value));

    assertTrue(error.getMessage().contains("Invalid XML character"));
    assertTrue(error.getMessage().contains(String.format("U+%04X", codePoint)));
  }

  @Test
  void rejectsUnpairedSurrogatesInsideOtherwiseValidText() {
    assertThrows(IllegalArgumentException.class, () -> XmlEscaper.escape("before\uD800after"));

    assertThrows(IllegalArgumentException.class, () -> XmlEscaper.escape("before\uDC00after"));
  }
}
