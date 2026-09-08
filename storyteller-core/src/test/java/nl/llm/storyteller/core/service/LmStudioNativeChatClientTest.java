package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LmStudioNativeChatClientTest {
  @Test
  @DisplayName("Given graph messages, when an LM Studio payload is built, then reasoning should be disabled")
  void shouldBuildReasoningDisabledPayload() {
    var client = new LmStudioNativeChatClient(
      "http://localhost:1234/v1/chat/completions", "graph-model", "", ChatRequestMetrics.NONE
    );

    Map<String, Object> payload = client.payload(
      List.of(new Message("system", "Extract JSON."), new Message("user", "Story context")),
      Map.of("temperature", 0.2, "max_tokens", 2048),
      "graph-model"
    );

    assertEquals("graph-model", payload.get("model"));
    assertEquals("Extract JSON.", payload.get("system_prompt"));
    assertEquals("Story context", payload.get("input"));
    assertEquals("off", payload.get("reasoning"));
    assertEquals(false, payload.get("store"));
    assertEquals(2048, payload.get("max_output_tokens"));
  }
}
