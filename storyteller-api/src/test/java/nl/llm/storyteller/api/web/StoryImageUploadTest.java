package nl.llm.storyteller.api.web;

import io.javalin.http.UploadedFile;
import jakarta.servlet.http.Part;
import nl.llm.storyteller.db.StoryImage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoryImageUploadTest {
  @Test
  void acceptsAnImageExactlyAtTheUploadLimit() throws Exception {
    byte[] content = png(StoryImage.MAX_UPLOAD_BYTES);
    assertEquals(new StoryImage("image/png", content), StoryImageUpload.read(upload(content.length, content)));
  }

  @Test
  void rejectsOversizedUploadsEvenIfTheReportedSizeIsSmaller() {
    byte[] content = png(StoryImage.MAX_UPLOAD_BYTES + 1);
    assertThrows(IllegalArgumentException.class, () -> StoryImageUpload.read(upload(content.length, content)));
    assertThrows(IllegalArgumentException.class, () -> StoryImageUpload.read(upload(1, content)));
  }

  @Test
  void keepsExistingImagesAboveTheNewUploadLimitReadable() {
    byte[] content = png(StoryImage.MAX_UPLOAD_BYTES + 1);
    StoryImage existing = new StoryImage("image/png", content);
    assertEquals(existing, StoryImage.fromDataUrl(existing.dataUrl()));
  }

  private byte[] png(int size) {
    byte[] content = new byte[size];
    byte[] image = java.util.Base64.getDecoder().decode(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jVj8AAAAASUVORK5CYII=");
    System.arraycopy(image, 0, content, 0, image.length);
    return content;
  }

  private UploadedFile upload(long reportedSize, byte[] content) {
    Part part = (Part) Proxy.newProxyInstance(Part.class.getClassLoader(), new Class<?>[]{Part.class},
      (_, method, _) -> switch (method.getName()) {
        case "getSize" -> reportedSize;
        case "getContentType" -> "image/png";
        case "getInputStream" -> new ByteArrayInputStream(content);
        default -> throw new UnsupportedOperationException(method.getName());
      });
    return new UploadedFile(part);
  }
}
