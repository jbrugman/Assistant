package nl.llm.storyteller.api.input;

import java.text.Normalizer;

public final class TextInputNormalizer {
  private TextInputNormalizer() { }

  public static String optionalSingleLine(String value, String field, int maximumLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return normalize(value, field, maximumLength, false);
  }

  public static String requiredMultiline(String value, String field, int maximumLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank.");
    }
    return normalize(value, field, maximumLength, true);
  }

  private static String normalize(String value, String field, int maximumLength, boolean multiline) {
    if (value.length() > maximumLength) {
      throw tooLong(field, maximumLength);
    }
    String normalized = Normalizer.normalize(value.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC)
      .trim();
    for (int index = 0; index < normalized.length(); index++) {
      char character = normalized.charAt(index);
      boolean allowedWhitespace = multiline && (character == '\n' || character == '\t');
      if (Character.isISOControl(character) && !allowedWhitespace) {
        throw new IllegalArgumentException(field + " contains an unsupported control character.");
      }
    }
    if (normalized.length() > maximumLength) {
      throw tooLong(field, maximumLength);
    }
    return normalized;
  }

  private static IllegalArgumentException tooLong(String field, int maximumLength) {
    return new IllegalArgumentException(field + " must not exceed " + maximumLength + " characters.");
  }
}
