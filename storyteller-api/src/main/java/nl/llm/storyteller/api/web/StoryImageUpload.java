package nl.llm.storyteller.api.web;

import io.javalin.http.UploadedFile;
import nl.llm.storyteller.db.StoryImage;

import java.io.IOException;
final class StoryImageUpload {
  static final long MAX_BYTES = StoryImage.MAX_UPLOAD_BYTES;

  private StoryImageUpload() {
  }

  static StoryImage read(UploadedFile upload) throws IOException {
    if (upload == null || upload.size() == 0) {
      return null;
    }
    if (upload.size() > MAX_BYTES) {
      throw new IllegalArgumentException("The pasted image must not exceed 5 MiB.");
    }
    byte[] content;
    try (var input = upload.content()) {
      content = input.readNBytes((int) MAX_BYTES + 1);
    }
    if (content.length > MAX_BYTES) {
      throw new IllegalArgumentException("The pasted image must not exceed 5 MiB.");
    }
    StoryImage image = new StoryImage(upload.contentType(), content);
    if (!image.mediaType().equals("image/png") && !image.mediaType().equals("image/jpeg")) {
      throw new IllegalArgumentException("Only PNG and JPEG images are supported.");
    }
    StoryImageDimensions.validate(image);
    return image;
  }
}
