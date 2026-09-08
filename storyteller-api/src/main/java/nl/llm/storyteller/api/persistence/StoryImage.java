package nl.llm.storyteller.api.persistence;

import org.jetbrains.annotations.NotNull;

import java.util.Base64;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public record StoryImage(String mediaType, byte[] content) {
  public static final int MAX_BYTES = 10 * 1024 * 1024;
  private static final String BASE64_SEPARATOR = ";base64,";
  private static final Map<String, byte[]> SIGNATURES = Map.of(
    "image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a},
    "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff},
    "image/gif", new byte[]{0x47, 0x49, 0x46, 0x38},
    "image/webp", new byte[]{0x52, 0x49, 0x46, 0x46}
  );

  public StoryImage {
    mediaType = normalizeMediaType(mediaType);
    if (content == null || content.length == 0 || content.length > MAX_BYTES) {
      throw new IllegalArgumentException("Image content must be between 1 byte and 10 MB.");
    }
    if (signatureDiffers(content, 0, SIGNATURES.get(mediaType))
      || ("image/webp".equals(mediaType)
        && signatureDiffers(content, 8, new byte[]{0x57, 0x45, 0x42, 0x50}))) {
      throw new IllegalArgumentException("Image content does not match its media type.");
    }
    content = content.clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }

  public String dataUrl() {
    return "data:" + mediaType + BASE64_SEPARATOR + Base64.getEncoder().encodeToString(content);
  }

  public static StoryImage fromDataUrl(String dataUrl) {
    if (dataUrl == null || !dataUrl.startsWith("data:") || !dataUrl.contains(BASE64_SEPARATOR)) {
      throw new IllegalArgumentException("Invalid image data URL.");
    }
    int separator = dataUrl.indexOf(BASE64_SEPARATOR);
    String mediaType = dataUrl.substring(5, separator);
    try {
      return new StoryImage(
        mediaType,
        Base64.getDecoder().decode(dataUrl.substring(separator + BASE64_SEPARATOR.length()))
      );
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid image data URL.", ex);
    }
  }

  private static String normalizeMediaType(String mediaType) {
    String normalized = mediaType == null
      ? ""
      : mediaType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    if (!SIGNATURES.containsKey(normalized)) {
      throw new IllegalArgumentException("Only PNG, JPEG, GIF, and WebP images are supported.");
    }
    return normalized;
  }

  private static boolean signatureDiffers(byte[] content, int offset, byte[] signature) {
    if (content.length < offset + signature.length) {
      return true;
    }
    for (int index = 0; index < signature.length; index++) {
      if (content[offset + index] != signature[index]) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object value) {
    return this == value || value instanceof StoryImage other
      && mediaType.equals(other.mediaType)
      && Arrays.equals(content, other.content);
  }

  @Override
  public int hashCode() {
    return 31 * mediaType.hashCode() + Arrays.hashCode(content);
  }

  @NotNull
  @Override
  public String toString() {
    return "StoryImage[mediaType=" + mediaType + ", content=" + Arrays.toString(content) + "]";
  }
}
