package nl.llm.storyteller.api.web;

import io.javalin.http.UploadedFile;
import nl.llm.storyteller.db.StoryImage;

import java.io.IOException;
final class StoryImageUpload {
  static final long MAX_BYTES = StoryImage.MAX_BYTES;

  private StoryImageUpload() {
  }

  static StoryImage read(UploadedFile upload) throws IOException {
    if (upload == null || upload.size() == 0) {
      return null;
    }
    if (upload.size() > MAX_BYTES) {
      throw new IllegalArgumentException("The pasted image must not exceed 10 MB.");
    }
    byte[] content;
    try (var input = upload.content()) {
      content = input.readNBytes((int) MAX_BYTES + 1);
    }
    if (content.length > MAX_BYTES) {
      throw new IllegalArgumentException("The pasted image must not exceed 10 MB.");
    }
    return new StoryImage(upload.contentType(), content);
  }
}
