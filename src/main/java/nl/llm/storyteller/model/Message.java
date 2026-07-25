package nl.llm.storyteller.model;

import java.util.Map;

public record Message(String role, String content) {
    public Map<String, String> toMap() {
        return Map.of("role", role, "content", content);
    }
}
