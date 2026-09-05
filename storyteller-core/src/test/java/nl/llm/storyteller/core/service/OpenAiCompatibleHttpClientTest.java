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
        Given an API key for an OpenAI-compatible endpoint,
        When the HTTP request is built,
        Then it should contain the API key as a bearer authorization header
        """)
    void shouldAddConfiguredApiKeyAsBearerToken() throws Exception {
        OpenAiCompatibleHttpClient client = new OpenAiCompatibleHttpClient(
            "https://example.test/v1/chat/completions", "test-model", true, "secret-api-key"
        );

        var request = client.buildRequest(List.of(new Message("user", "hello")), Map.of(), 30);

        assertEquals("Bearer secret-api-key", request.headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    @DisplayName("""
        Given no API key for a local OpenAI-compatible endpoint,
        When the HTTP request is built,
        Then it should omit the authorization header
        """)
    void shouldOmitAuthorizationHeaderWhenApiKeyIsBlank() throws Exception {
        OpenAiCompatibleHttpClient client = new OpenAiCompatibleHttpClient(
            "http://localhost:1234/v1/chat/completions", "test-model", true, ""
        );

        var request = client.buildRequest(List.of(new Message("user", "hello")), Map.of(), 30);

        assertTrue(request.headers().firstValue("Authorization").isEmpty());
    }

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

    @Test
    @DisplayName("""
        Given a transient image message,
        When the OpenAI-compatible request payload is built,
        Then the message should contain text and image content parts
        """)
    void shouldBuildMultimodalMessageContent() {
        OpenAiCompatibleHttpClient client = new OpenAiCompatibleHttpClient(
            "http://localhost:1234/v1/chat/completions", "vision-model", true
        );

        Map<String, Object> payload = client.buildPayload(
            List.of(Message.withImage("user", "Describe the island.", "data:image/png;base64,AAAA")),
            Map.of()
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.get("messages");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) messages.getFirst().get("content");
        assertEquals(Map.of("type", "text", "text", "Describe the island."), content.getFirst());
        assertEquals(
            Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64,AAAA")),
            content.getLast()
        );
    }
}
