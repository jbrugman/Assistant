package nl.llm.storyteller.core.model;

import java.util.List;
import java.util.Map;

public record Message(
  String role,
  String content,
  String imageDataUrl
) {
    public Message(String role, String content) {
        this(role, content, "");
    }

    public static Message withImage(String role, String content, String imageDataUrl) {
        if (imageDataUrl == null || imageDataUrl.isBlank()) {
            throw new IllegalArgumentException("Image data URL must not be blank.");
        }
        return new Message(role, content, imageDataUrl);
    }

    public Map<String, Object> toMap() {
        if (imageDataUrl == null || imageDataUrl.isBlank()) {
            return Map.of("role", role, "content", content);
        }
        return Map.of(
          "role", role,
          "content", List.of(
            Map.of("type", "text", "text", content),
            Map.of("type", "image_url", "image_url", Map.of("url", imageDataUrl))
          )
        );
    }
}
