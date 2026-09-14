package nl.llm.storyteller.core.service.openai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiBackendIdentityTest {
  @Test
  @DisplayName("""
    Given an OpenAI-compatible Chat Completions URL,
    When the models endpoint is derived,
    Then it should retain the server and replace only the route suffix
    """)
  void shouldDeriveModelsEndpoint() {
    assertEquals(
      "http://localhost:1234/v1/models",
      OpenAiBackendIdentity.modelsUri("http://localhost:1234/v1/chat/completions").toString()
    );
  }

  @Test
  @DisplayName("""
    Given model-list responses from oMLX and another compatible backend,
    When backend ownership is inspected,
    Then only the oMLX response should be identified as oMLX
    """)
  void shouldDetectOmlxFromModelOwnership() throws Exception {
    assertTrue(OpenAiBackendIdentity.containsOmlxModel("""
      {"data":[{"id":"gemma","owned_by":"omlx"}]}
      """));
    assertFalse(OpenAiBackendIdentity.containsOmlxModel("""
      {"data":[{"id":"gemma","owned_by":"lmstudio"}]}
      """));
  }
}
