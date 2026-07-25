package nl.llm.storyteller;

import java.util.Map;

record Message(String role, String content) {
    Map<String, String> toMap() {
        return Map.of("role", role, "content", content);
    }
}
