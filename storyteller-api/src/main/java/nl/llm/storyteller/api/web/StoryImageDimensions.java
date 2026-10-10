package nl.llm.storyteller.api.web;

import nl.llm.storyteller.db.StoryImage;

import java.nio.charset.StandardCharsets;

final class StoryImageDimensions {
  private StoryImageDimensions() {
  }

  static void validate(StoryImage image) {
    byte[] bytes = image.content();
    int[] dimensions = switch (image.mediaType()) {
      case "image/png" -> png(bytes);
      case "image/jpeg" -> jpeg(bytes);
      default -> throw invalid();
    };
    if (dimensions[0] <= 0 || dimensions[1] <= 0) throw invalid();
    if (dimensions[0] > 1024 || dimensions[1] > 768) {
      throw new IllegalArgumentException("The image must fit within 1024 × 768 pixels.");
    }
  }

  private static int[] png(byte[] bytes) {
    if (bytes.length < 33 || !type(bytes, 12).equals("IHDR") || bigEndian(bytes, 8, 4) != 13) throw invalid();
    return new int[]{bigEndian(bytes, 16, 4), bigEndian(bytes, 20, 4)};
  }

  private static int[] jpeg(byte[] bytes) {
    int index = 2;
    while (index < bytes.length) {
      if ((bytes[index++] & 255) != 255) throw invalid();
      while (index < bytes.length && (bytes[index] & 255) == 255) index++;
      if (index >= bytes.length) throw invalid();
      int marker = bytes[index++] & 255;
      if (marker == 0xd9 || marker == 0xda) throw invalid();
      if (marker == 1 || marker >= 0xd0 && marker <= 0xd7) continue;
      if (index + 2 > bytes.length) throw invalid();
      int length = bigEndian(bytes, index, 2);
      if (length < 2 || index + length > bytes.length) throw invalid();
      // SOF markers describe frame dimensions. C4, C8 and CC are not frame headers.
      if (marker >= 0xc0 && marker <= 0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc) {
        if (length < 8) throw invalid();
        return new int[]{bigEndian(bytes, index + 5, 2), bigEndian(bytes, index + 3, 2)};
      }
      index += length;
    }
    throw invalid();
  }

  private static String type(byte[] bytes, int offset) {
    return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
  }

  private static int bigEndian(byte[] bytes, int offset, int length) {
    int value = 0;
    for (int i = 0; i < length; i++) value = (value << 8) | (bytes[offset + i] & 255);
    return value;
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Could not read the image dimensions.");
  }
}
