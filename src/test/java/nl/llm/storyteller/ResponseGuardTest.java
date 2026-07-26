package nl.llm.storyteller;

import nl.llm.storyteller.model.Message;
import nl.llm.storyteller.service.ChatClient;
import nl.llm.storyteller.service.ResponseGuard;
import nl.llm.storyteller.service.ResponseSanitizer;
import nl.llm.storyteller.service.ValidationDecisionParser;
import nl.llm.storyteller.service.ValidationOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
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
}
