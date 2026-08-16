package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleHttpClientTest {
    @Test
    @DisplayName("""
        Given a configured chat model name,
        When the request payload is built,
        Then the payload should contain that explicit model value
        """)
    void shouldIncludeConfiguredModelInPayload() {
        OpenAiCompatibleHttpClient client = new OpenAiCompatibleHttpClient(
            "http://localhost:1234/v1/chat/completions", "test-model", true
        );

        Map<String, Object> payload = client.buildPayload(List.of(new Message("user", "hello")), Map.of("temperature", 0.6));

        assertEquals("test-model", payload.get("model"));
    }

    @Test
    @DisplayName("""
        Given a blank configured model name,
        When the request payload is built,
        Then the model field should be omitted so the backend can choose the active default model
        """)
    void shouldOmitModelFromPayloadWhenConfigurationIsBlank() {
        OpenAiCompatibleHttpClient client = new OpenAiCompatibleHttpClient(
            "http://localhost:1234/v1/chat/completions", "", true
        );

        Map<String, Object> payload = client.buildPayload(List.of(new Message("user", "hello")), Map.of("temperature", 0.6));

        assertFalse(payload.containsKey("model"));
        assertTrue(payload.containsKey("messages"));
    }
}
