package nl.llm.storyteller.api.web;

import nl.llm.storyteller.db.StoryImage;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoryImageDimensionsTest {
  @Test
  void acceptsTheExactBoundsAndRejectsWidthHeightAndTruncatedHeaders() {
    for (String type : new String[]{"image/png", "image/jpeg"}) {
      assertDoesNotThrow(() -> StoryImageDimensions.validate(image(type, 1024, 768)));
      assertThrows(IllegalArgumentException.class, () -> StoryImageDimensions.validate(image(type, 1025, 768)));
      assertThrows(IllegalArgumentException.class, () -> StoryImageDimensions.validate(image(type, 1024, 769)));
    }
    assertThrows(IllegalArgumentException.class, () -> StoryImageDimensions.validate(
      new StoryImage("image/png", Base64.getDecoder().decode("iVBORw0KGgo="))));
  }

  private StoryImage image(String type, int width, int height) {
    if (type.equals("image/png")) {
      byte[] bytes = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jVj8AAAAASUVORK5CYII=");
      ByteBuffer.wrap(bytes).putInt(16, width).putInt(20, height);
      return new StoryImage(type, bytes);
    }
    if (type.equals("image/jpeg")) {
      // SOI, an APP segment, then a progressive SOF with height and width.
      byte[] bytes = {(byte) 255, (byte) 0xd8, (byte) 255, (byte) 0xe0, 0, 4, 0, 0,
        (byte) 255, (byte) 0xc2, 0, 8, 8, 0, 0, 0, 0, 1};
      ByteBuffer.wrap(bytes).putShort(13, (short) height).putShort(15, (short) width);
      return new StoryImage(type, bytes);
    }
    throw new IllegalArgumentException(type);
  }
}
