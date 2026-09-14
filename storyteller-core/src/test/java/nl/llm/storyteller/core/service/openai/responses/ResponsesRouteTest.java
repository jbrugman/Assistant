package nl.llm.storyteller.core.service.openai.responses;

import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.service.openai.OpenAiRouteResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsesRouteTest {
  @Test
  @DisplayName("""
    Given a Chat Completions URL and generation options,
    When a Responses payload is built,
    Then the Responses URL and equivalent option names should be used
    """)
  void shouldDeriveResponsesUrlAndMapOptions() {
    ResponsesRoute route = new ResponsesRoute(
      "http://localhost:1234/v1/chat/completions", "gemma", "", true
    );
    Map<String, Object> payload = route.buildPayload(
      List.of(new Message("system", "rules"), new Message("user", "hello")),
      Map.of("max_tokens", 512, "temperature", 0.2)
    );

    assertEquals("http://localhost:1234/v1/responses", route.uri().toString());
    assertEquals(512, payload.get("max_output_tokens"));
    assertEquals(Map.of("effort", "none"), payload.get("reasoning"));
    assertFalse(payload.containsKey("max_tokens"));
    assertTrue(payload.containsKey("input"));
  }

  @Test
  @DisplayName("""
    Given a message containing text and an image,
    When a Responses payload is built,
    Then both inputs should use the Responses content types
    """)
  void shouldMapVisionInputToResponsesContentTypes() {
    ResponsesRoute route = new ResponsesRoute(
      "http://localhost:1234/v1/chat/completions", "vision", "", false
    );
    Map<String, Object> payload = route.buildPayload(
      List.of(Message.withImage("user", "Describe", "data:image/png;base64,AAAA")), Map.of()
    );

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> input = (List<Map<String, Object>>) payload.get("input");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> content = (List<Map<String, Object>>) input.getFirst().get("content");
    assertEquals("input_text", content.getFirst().get("type"));
    assertEquals("input_image", content.getLast().get("type"));
  }

  @Test
  @DisplayName("""
    Given a Responses response containing reasoning usage,
    When the response is parsed,
    Then output and thinking tokens should be retained
    """)
  void shouldRetainResponsesThinkingTokens() {
    ResponsesRoute route = new ResponsesRoute(
      "http://localhost:1234/v1/chat/completions", "gemma", "", false
    );

    OpenAiRouteResult result = route.parseResponse("""
      {"output":[{"type":"message","content":[{"type":"output_text","text":"OK"}]}],
      "usage":{"output_tokens":14,"output_tokens_details":{"reasoning_tokens":11}}}
      """);

    assertEquals(14, result.outputTokens());
    assertEquals(11, result.reasoningTokens());
  }
}
