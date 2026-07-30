package nl.llm.storyteller;

import nl.llm.storyteller.model.Message;
import nl.llm.storyteller.model.ValidationOutcome;
import nl.llm.storyteller.service.ChatClient;
import nl.llm.storyteller.service.ResponseGuard;
import nl.llm.storyteller.service.ResponseSanitizer;
import nl.llm.storyteller.service.ValidationDecisionParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseGuardTest {
  @ParameterizedTest
  @CsvSource(
    delimiter = '|',
    textBlock = """
      ALLOW|Candidate response|Candidate response
      '{"decision":"REPLACE","response":"Corrected response"}'|Candidate response|Corrected response
      'Corrected replacement response'|Candidate response|Corrected replacement response
      '{"decision":"ALLOW"}'|'Line one\\nLine two'|'Line one
      Line two'
      """
  )
  @DisplayName("""
    Given validator outcomes that allow, rewrite, or sanitize the candidate response,
    When the response guard validates a candidate story response,
    Then the expected final response should be returned
    """)
  void shouldReturnExpectedValidatedResponse(String validatorPayload, String candidateResponse, String expectedResponse) throws Exception {
    ResponseGuard responseGuard = new ResponseGuard(
      new FakeChatClient(validatorPayload),
      AppConfig.load()
    );

    String validatedResponse = responseGuard.validate(
      "validator system prompt",
      "validator request",
      candidateResponse
    );

    assertEquals(expectedResponse, validatedResponse);
  }

  @Test
  @DisplayName("""
    Given validation is disabled in configuration,
    When the response guard validates a candidate story response,
    Then the candidate response should be sanitized and returned without calling the validator backend
    """)
  void shouldBypassValidatorBackendWhenValidationIsDisabled() throws Exception {
    Path baseDirectory = Files.createTempDirectory("storyteller-validation-disabled");
    Path configFile = baseDirectory.resolve("systemprompts/application.config");
    Files.createDirectories(configFile.getParent());
    Files.writeString(configFile, "validation.enabled=false");

    ResponseGuard responseGuard = new ResponseGuard(
      new ThrowingChatClient(),
      AppConfigLoader.load(baseDirectory, null)
    );

    String validatedResponse = responseGuard.validate(
      "validator system prompt",
      "validator request",
      "Line one\\nLine two"
    );

    assertEquals("Line one\nLine two", validatedResponse);
  }

  @ParameterizedTest
  @CsvSource(
    delimiter = '|',
    textBlock = """
      ALLOW|ALLOW
      REPLACE|REPLACE
      BLOCK|BLOCK
      {"decision":"ALLOW"}|ALLOW
      {"decision":"REPLACE","response":"fixed"}|REPLACE
      "{\\"decision\\":\\"ALLOW\\"}"|ALLOW
      validator says REPLACE because of continuity|REPLACE
      """
  )
  @DisplayName("""
    Given different validator payload shapes,
    When the validation decision is parsed,
    Then the allow or block decision should be recognized consistently
    """)
  void shouldParseValidationDecisionsAcrossDifferentPayloadShapes(String payload, String expectedDecision) {
    ValidationDecisionParser parser = new ValidationDecisionParser();

    assertEquals(expectedDecision, Objects.requireNonNull(parser.parse(payload)).decision());
  }

  @Test
  @DisplayName("""
    Given a validator payload with a leading REPLACE marker and an embedded JSON rewrite response,
    When the validation decision is parsed,
    Then the embedded replacement response should be extracted and used
    """)
  void shouldExtractReplacementTextFromEmbeddedJsonAfterReplaceMarker() {
    ValidationDecisionParser parser = new ValidationDecisionParser();

    ValidationOutcome outcome = parser.parse("""
      REPLACE
      
      {"decision":"REPLACE","response":"Corrected response"}
      """);

    Assertions.assertNotNull(outcome);
    assertEquals("REPLACE", outcome.decision());
    assertEquals("Corrected response", outcome.replacementText());
  }

  @ParameterizedTest
  @CsvSource(
    delimiter = '|',
    textBlock = """
      'REPLACE: Fixed line'|Fixed line
      'REPLACE - Fixed line'|Fixed line
      'REPLACE
      Fixed line'|Fixed line
      'Maybe this is fine.'|Maybe this is fine.
      '{"content":"REPLACE\\n\\n{\\"decision\\":\\"REPLACE\\",\\"response\\":\\"Corrected response\\"}"}'|Corrected response
      '{"message":{"role":"assistant","content":"REPLACE\\n\\n{\\"decision\\":\\"REPLACE\\",\\"response\\":\\"Corrected response\\"}"}}'|Corrected response
      '{
        "id": "chatcmpl-test",
        "object": "chat.completion",
        "created": 1785225311,
        "model": "google/gemma-4-12b-qat",
        "choices": [
          {
            "index": 0,
            "message": {
              "role": "assistant",
              "content": "REPLACE\\n\\n{\\"decision\\":\\"REPLACE\\",\\"response\\":\\"Corrected response\\"}"
            }
          }
        ]
      }'|Corrected response
      '{"decision":"REPLACE","response":"Fixed line"}'|Fixed line
      """
  )
  @DisplayName("""
    Given validator payloads that contain rewrite text in different plain or wrapped forms,
    When the validation decision is parsed,
    Then the replacement text should still be extracted consistently
    """)
  void shouldExtractReplacementTextAcrossPayloadShapes(String payload, String expectedReplacement) {
    ValidationDecisionParser parser = new ValidationDecisionParser();

    ValidationOutcome outcome = parser.parse(payload);

    Assertions.assertNotNull(outcome);
    assertEquals("REPLACE", outcome.decision());
    assertEquals(expectedReplacement, outcome.replacementText());
  }

  @Test
  @DisplayName("""
    Given a JSON validator payload without an explicit decision but with replacement text,
    When the validation decision is parsed,
    Then it should still be treated as a replace outcome
    """)
  void shouldTreatJsonReplacementPayloadWithoutDecisionAsReplace() {
    ValidationDecisionParser parser = new ValidationDecisionParser();

    ValidationOutcome outcome = parser.parse("{\"response\":\"Fixed line\"}");

    Assertions.assertNotNull(outcome);
    assertEquals("REPLACE", outcome.decision());
    assertEquals("Fixed line", outcome.replacementText());
  }

  @ParameterizedTest
  @CsvSource(
    delimiter = '|',
    textBlock = """
      '  Plain response  '|Plain response
      'Line one\\nLine two'|'Line one
      Line two'
      'Escaped quote: \\"Hello\\"'|'Escaped quote: "Hello"'
      """
  )
  @DisplayName("""
    Given candidate responses with visible escape sequences or extra whitespace,
    When the response sanitizer normalizes them,
    Then the returned text should be cleaner for terminal output
    """)
  void shouldSanitizeVisibleEscapesAndTrimWhitespace(String rawResponse, String expectedResponse) {
    ResponseSanitizer sanitizer = new ResponseSanitizer();

    assertEquals(expectedResponse, sanitizer.sanitize(rawResponse));
  }

  private record FakeChatClient(String response) implements ChatClient {
    @Override
    public String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds) {
      return response;
    }
  }

  private record ThrowingChatClient() implements ChatClient {
    @Override
    public String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds) {
      throw new AssertionError("Validation backend should not be called when validation is disabled.");
    }
  }
}
