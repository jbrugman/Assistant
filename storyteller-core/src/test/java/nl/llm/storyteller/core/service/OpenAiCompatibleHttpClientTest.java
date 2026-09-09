package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleHttpClientTest {
    @ParameterizedTest
    @CsvSource(value = {
        "<think>Do not show this.</think>Visible response;Visible response",
        "<|thinking|>Do not show this.</|thinking|>Visible response;Visible response",
        "<|channel>thought Do not show this.\\nStill reasoning.<channel|>Visible response;Visible response",
        "<model-reasoning-output>Do not show this.</model-reasoning-output>Visible response;Visible response",
        "<|channel>thought Chris (the male protagonist).;"
    }, delimiter = ';')
    @DisplayName("""
        Given reasoning emitted through a supported reasoning or channel tag format,
        When reasoning blocks are hidden,
        Then only the visible final response should remain
        """)
    void shouldRemoveReasoningTagFormats(String response, String expected) {
        OpenAiCompatibleHttpClient client = new OpenAiCompatibleHttpClient(
            "http://localhost:1234/v1/chat/completions", "test-model", true
        );

        String sanitized = client.stripReasoningBlocks(response);

        assertEquals(expected == null ? "" : expected, sanitized);
    }

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
    @DisplayName("Given a Gemini OpenAI endpoint, when a payload is built, then unsupported sampler options are omitted")
    void shouldOmitUnsupportedSamplerOptionsForGemini() {
        OpenAiCompatibleHttpClient client = new OpenAiCompatibleHttpClient(
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-model", true
        );

        Map<String, Object> payload = client.buildPayload(
            List.of(new Message("user", "hello")),
            Map.of(
                "temperature", 0.6,
                "top_p", 0.9,
                "top_k", 40,
                "min_p", 0.05,
                "repeat_penalty", 1.1,
                "max_tokens", 1024
            )
        );

        assertEquals(0.6, payload.get("temperature"));
        assertEquals(0.9, payload.get("top_p"));
        assertEquals(1024, payload.get("max_tokens"));
        assertFalse(payload.containsKey("top_k"));
        assertFalse(payload.containsKey("min_p"));
        assertFalse(payload.containsKey("repeat_penalty"));
    }

    @Test
    @DisplayName("Given google.backend=true, when a proxied payload is built, then Gemini sampler options are omitted")
    void shouldOmitUnsupportedSamplerOptionsForExplicitGoogleBackend() {
        OpenAiCompatibleHttpClient client = new OpenAiCompatibleHttpClient(
            "https://gateway.example.test/v1/chat/completions", "gemini-model", true, "",
            ChatRequestMetrics.NONE, "generation", true
        );

        Map<String, Object> payload = client.buildPayload(
            List.of(new Message("user", "hello")),
            Map.of("top_k", 40, "min_p", 0.05, "repeat_penalty", 1.1)
        );

        assertFalse(payload.containsKey("top_k"));
        assertFalse(payload.containsKey("min_p"));
        assertFalse(payload.containsKey("repeat_penalty"));
    }

    @Test
    @DisplayName("Given a non-Gemini endpoint, when a payload is built, then backend sampler options are preserved")
    void shouldPreserveSamplerOptionsForOtherBackends() {
        OpenAiCompatibleHttpClient client = new OpenAiCompatibleHttpClient(
            "http://localhost:1234/v1/chat/completions", "local-model", true
        );
        Map<String, Object> options = Map.of("top_k", 40, "min_p", 0.05, "repeat_penalty", 1.1);

        Map<String, Object> payload = client.buildPayload(List.of(new Message("user", "hello")), options);

        assertEquals(40, payload.get("top_k"));
        assertEquals(0.05, payload.get("min_p"));
        assertEquals(1.1, payload.get("repeat_penalty"));
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
