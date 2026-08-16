package nl.llm.storyteller.core;

import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.service.ChatClient;
import nl.llm.storyteller.core.service.StructuredOutputNotSupportedException;
import nl.llm.storyteller.core.service.ValidationClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationClientTest {
  @Test
  @DisplayName("""
    Given automatic structured validator output,
    When a validation request is sent,
    Then it should include the OpenAI-compatible JSON Schema response format
    """)
  void shouldRequestJsonSchemaForAutomaticValidationOutput() throws Exception {
    RecordingChatClient client = new RecordingChatClient("{\"decision\":\"ALLOW\",\"response\":\"\"}");

    new ValidationClient(client, AppConfig.load()).validate("system", "request");

    Map<String, Object> responseFormat = responseFormat(client.options().getFirst());
    assertEquals("json_schema", responseFormat.get("type"));
    assertTrue(client.options().getFirst().containsKey("response_format"));
  }

  @Test
  @DisplayName("""
    Given an automatic structured-output request rejected by a legacy backend,
    When validation is sent,
    Then the client should retry once without the response format
    """)
  void shouldRetryAsTextWhenAutomaticStructuredOutputIsUnsupported() throws Exception {
    FallbackChatClient client = new FallbackChatClient();
    ValidationClient validationClient = new ValidationClient(client, AppConfig.load());

    String result = validationClient.validate("system", "request");
    validationClient.validate("system", "another request");

    assertEquals("ALLOW", result);
    assertEquals(3, client.options().size());
    assertTrue(client.options().getFirst().containsKey("response_format"));
    assertFalse(client.options().get(1).containsKey("response_format"));
    assertFalse(client.options().getLast().containsKey("response_format"));
  }

  @Test
  @DisplayName("""
    Given JSON Schema output is explicitly required,
    When the backend rejects the response format,
    Then validation should not silently fall back to plain text
    """)
  void shouldNotFallbackWhenJsonSchemaOutputIsRequired() throws Exception {
    Path baseDirectory = Files.createTempDirectory("storyteller-validation-schema-required");
    Path configFile = baseDirectory.resolve("systemprompts/application.config");
    Files.createDirectories(configFile.getParent());
    Files.writeString(configFile, "validation.outputMode=json-schema");
    FallbackChatClient client = new FallbackChatClient();

    assertThrows(
      StructuredOutputNotSupportedException.class,
      () -> new ValidationClient(client, AppConfigLoader.load(baseDirectory, null)).validate("system", "request")
    );
    assertEquals(1, client.options().size());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> responseFormat(Map<String, Object> options) {
    return (Map<String, Object>) options.get("response_format");
  }

  private static final class RecordingChatClient implements ChatClient {
    private final String response;
    private final List<Map<String, Object>> options = new ArrayList<>();

    private RecordingChatClient(String response) {
      this.response = response;
    }

    @Override
    public String chat(List<Message> messages, Map<String, Object> requestOptions, int timeoutSeconds) {
      options.add(requestOptions);
      return response;
    }

    private List<Map<String, Object>> options() {
      return options;
    }
  }

  private static final class FallbackChatClient implements ChatClient {
    private final List<Map<String, Object>> options = new ArrayList<>();

    @Override
    public String chat(List<Message> messages, Map<String, Object> requestOptions, int timeoutSeconds) throws IOException {
      options.add(requestOptions);
      if (requestOptions.containsKey("response_format")) {
        throw new StructuredOutputNotSupportedException("unsupported");
      }
      return "ALLOW";
    }

    private List<Map<String, Object>> options() {
      return options;
    }
  }
}
