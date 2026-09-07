package nl.llm.storyteller.api.input;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextInputNormalizerTest {
  @Test
  @DisplayName("""
    Given multiline web input with Windows line endings and decomposed Unicode,
    When the input is normalized,
    Then line endings and Unicode should have one canonical representation
    """)
  void shouldNormalizeMultilineInput() {
    String normalized = TextInputNormalizer.requiredMultiline(" Cafe\u0301\r\nnext\rline ", "Prompt", 100);

    assertEquals("Café\nnext\nline", normalized);
  }

  @ParameterizedTest
  @ValueSource(strings = {"bad\u0000value", "bad\u0007value", "bad\u001Bvalue"})
  @DisplayName("""
    Given web input containing a control character,
    When the input is normalized,
    Then it should be rejected before reaching a service or repository
    """)
  void shouldRejectControlCharacters(String input) {
    assertThrows(
      IllegalArgumentException.class,
      () -> TextInputNormalizer.requiredMultiline(input, "Prompt", 100)
    );
  }

  @Test
  @DisplayName("""
    Given story text that resembles SQL,
    When the input is normalized,
    Then its ordinary printable content should remain unchanged
    """)
  void shouldPreservePrintableStoryText() {
    String input = "Robert'); DROP TABLE story_session;--";

    assertEquals(input, TextInputNormalizer.requiredMultiline(input, "Prompt", 100));
  }
}
