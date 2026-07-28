package nl.llm.storyteller;

import nl.llm.storyteller.model.Message;
import nl.llm.storyteller.service.ChatClient;
import nl.llm.storyteller.service.ResponseGuard;
import nl.llm.storyteller.service.ResponseSanitizer;
import nl.llm.storyteller.service.ValidationDecisionParser;
import nl.llm.storyteller.model.ValidationOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResponseGuardTest {
    @Test
    @DisplayName("""
        Given a validation backend that returns ALLOW,
        When the response guard validates a candidate story response,
        Then the original candidate response should be returned
        """)
    void shouldReturnCandidateResponseWhenValidationAllowsIt() throws Exception {
        ResponseGuard responseGuard = new ResponseGuard(
            new FakeChatClient("ALLOW"),
            AppConfig.load()
        );

        String validatedResponse = responseGuard.validate(
            "validator system prompt",
            "validator request",
            "Candidate response"
        );

        assertEquals("Candidate response", validatedResponse);
    }

    @Test
    @DisplayName("""
        Given a validation backend that returns REPLACE with corrected text,
        When the response guard validates a candidate story response,
        Then the replacement text should be returned instead of the original candidate response
        """)
    void shouldReturnReplacementTextWhenValidationRequestsRewrite() throws Exception {
        ResponseGuard responseGuard = new ResponseGuard(
            new FakeChatClient("{\"decision\":\"REPLACE\",\"response\":\"Corrected response\"}"),
            AppConfig.load()
        );

        String validatedResponse = responseGuard.validate(
            "validator system prompt",
            "validator request",
            "Candidate response"
        );

        assertEquals("Corrected response", validatedResponse);
    }

    @Test
    @DisplayName("""
        Given a validation backend that returns direct corrected prose without a decision wrapper,
        When the response guard validates a candidate story response,
        Then that corrected prose should be treated as replacement text
        """)
    void shouldUseDirectRewriteTextWhenValidatorReturnsPlainReplacementText() throws Exception {
        ResponseGuard responseGuard = new ResponseGuard(
            new FakeChatClient("Corrected replacement response"),
            AppConfig.load()
        );

        String validatedResponse = responseGuard.validate(
            "validator system prompt",
            "validator request",
            "Candidate response"
        );

        assertEquals("Corrected replacement response", validatedResponse);
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

    @Test
    @DisplayName("""
        Given a validation backend that returns ALLOW and a candidate response with visible JSON escapes,
        When the response guard validates the candidate story response,
        Then the final response should be unescaped before it is returned
        """)
    void shouldUnescapeAllowedCandidateResponsesBeforeReturningThem() throws Exception {
        ResponseGuard responseGuard = new ResponseGuard(
            new FakeChatClient("{\"decision\":\"ALLOW\"}"),
            AppConfig.load()
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

        assertEquals(expectedDecision, parser.parse(payload).decision());
    }

    @Test
    @DisplayName("""
        Given a validator payload with plain non-empty text and no explicit decision,
        When the validation decision is parsed,
        Then that text should be treated as replacement content
        """)
    void shouldTreatPlainNonEmptyValidatorTextAsReplacementContent() {
        ValidationDecisionParser parser = new ValidationDecisionParser();

        ValidationOutcome outcome = parser.parse("Maybe this is fine.");

        assertEquals("REPLACE", outcome.decision());
        assertEquals("Maybe this is fine.", outcome.replacementText());
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        textBlock = """
            'REPLACE: Fixed line'|Fixed line
            'REPLACE - Fixed line'|Fixed line
            """
    )
    @DisplayName("""
        Given a validator payload that starts with REPLACE followed by corrected prose on the same line,
        When the validation decision is parsed,
        Then the corrected prose should be preserved as replacement text
        """)
    void shouldExtractTrailingReplacementTextAfterReplaceDecisionOnSameLine(String payload, String expectedReplacement) {
        ValidationDecisionParser parser = new ValidationDecisionParser();

        ValidationOutcome outcome = parser.parse(payload);

        assertEquals("REPLACE", outcome.decision());
        assertEquals(expectedReplacement, outcome.replacementText());
    }

    @Test
    @DisplayName("""
        Given a validator payload that starts with REPLACE followed by corrected prose on the next line,
        When the validation decision is parsed,
        Then the corrected prose should be preserved as replacement text
        """)
    void shouldExtractTrailingReplacementTextAfterReplaceDecisionOnNextLine() {
        ValidationDecisionParser parser = new ValidationDecisionParser();

        ValidationOutcome outcome = parser.parse("REPLACE\nFixed line");

        assertEquals("REPLACE", outcome.decision());
        assertEquals("Fixed line", outcome.replacementText());
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

        assertEquals("REPLACE", outcome.decision());
        assertEquals("Corrected response", outcome.replacementText());
    }

    @Test
    @DisplayName("""
        Given a validator payload wrapped in a top-level content field,
        When the validation decision is parsed,
        Then the nested rewrite response should still be extracted
        """)
    void shouldExtractReplacementTextFromTopLevelContentWrapper() {
        ValidationDecisionParser parser = new ValidationDecisionParser();

        ValidationOutcome outcome = parser.parse("""
            {"content":"REPLACE\\n\\n{\\"decision\\":\\"REPLACE\\",\\"response\\":\\"Corrected response\\"}"}
            """);

        assertEquals("REPLACE", outcome.decision());
        assertEquals("Corrected response", outcome.replacementText());
    }

    @Test
    @DisplayName("""
        Given a validator payload wrapped in a nested message content field,
        When the validation decision is parsed,
        Then the nested rewrite response should still be extracted
        """)
    void shouldExtractReplacementTextFromNestedMessageContentWrapper() {
        ValidationDecisionParser parser = new ValidationDecisionParser();

        ValidationOutcome outcome = parser.parse("""
            {"message":{"role":"assistant","content":"REPLACE\\n\\n{\\"decision\\":\\"REPLACE\\",\\"response\\":\\"Corrected response\\"}"}}
            """);

        assertEquals("REPLACE", outcome.decision());
        assertEquals("Corrected response", outcome.replacementText());
    }

    @Test
    @DisplayName("""
        Given a replace payload with replacement text,
        When the validation decision is parsed,
        Then the replacement text should be extracted as part of the validation outcome
        """)
    void shouldExtractReplacementTextFromValidationOutcome() {
        ValidationDecisionParser parser = new ValidationDecisionParser();

        ValidationOutcome outcome = parser.parse("{\"decision\":\"REPLACE\",\"response\":\"Fixed line\"}");

        assertEquals("REPLACE", outcome.decision());
        assertEquals("Fixed line", outcome.replacementText());
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
        public String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds) throws IOException {
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
