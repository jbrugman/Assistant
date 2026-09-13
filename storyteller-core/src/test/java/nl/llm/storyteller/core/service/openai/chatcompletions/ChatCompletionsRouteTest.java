package nl.llm.storyteller.core.service.openai.chatcompletions;

import nl.llm.storyteller.core.model.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatCompletionsRouteTest {
  @Test
  void buildsAuthenticatedChatCompletionsRequest() throws Exception {
    ChatCompletionsRoute route = new ChatCompletionsRoute(
      "https://example.test/v1/chat/completions", "test-model", "secret", false, false
    );
    var request = route.buildRequest(List.of(new Message("user", "hello")), Map.of("temperature", 0.6), 30);

    assertEquals("Bearer secret", request.headers().firstValue("Authorization").orElseThrow());
    assertTrue(request.bodyPublisher().isPresent());
  }

  @Test
  void filtersGeminiOptions() {
    ChatCompletionsRoute route = new ChatCompletionsRoute(
      "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini", "", false, false
    );
    Map<String, Object> payload = route.buildPayload(List.of(new Message("user", "hello")), Map.of(
      "temperature", 0.6, "top_k", 40, "min_p", 0.05, "repeat_penalty", 1.1
    ));

    assertEquals(0.6, payload.get("temperature"));
    assertFalse(payload.containsKey("top_k"));
    assertFalse(payload.containsKey("min_p"));
    assertFalse(payload.containsKey("repeat_penalty"));
  }

  @Test
  void disablesReasoningOnlyWhenRequested() {
    ChatCompletionsRoute route = new ChatCompletionsRoute(
      "http://localhost:1234/v1/chat/completions", "gemma", "", false, true
    );
    Map<String, Object> payload = route.buildPayload(
      List.of(new Message("user", "hello")), Map.of("reasoning_effort", "medium")
    );

    assertEquals("none", payload.get("reasoning_effort"));
    assertFalse(payload.containsKey("store"));
  }
}
