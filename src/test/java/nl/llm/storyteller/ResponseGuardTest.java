package nl.llm.storyteller;

import nl.llm.storyteller.model.Message;
import nl.llm.storyteller.service.ChatClient;
import nl.llm.storyteller.service.ResponseGuard;
import nl.llm.storyteller.service.ResponseSanitizer;
import nl.llm.storyteller.service.ValidationDecisionParser;
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
        Given a validation backend that returns BLOCK,
        When the response guard validates a candidate story response,
        Then the configured validation fallback message should be returned
        """)
    void shouldReturnFallbackMessageWhenValidationBlocksIt() throws Exception {
        AppConfig config = AppConfig.load();
        ResponseGuard responseGuard = new ResponseGuard(
            new FakeChatClient("BLOCK"),
            config
        );

        String validatedResponse = responseGuard.validate(
            "validator system prompt",
            "validator request",
            "Candidate response"
        );

        assertEquals(config.validationFailClosedMessage(), validatedResponse);
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
            BLOCK|BLOCK
            {"decision":"ALLOW"}|ALLOW
            {"decision":"BLOCK"}|BLOCK
            "{\\"decision\\":\\"ALLOW\\"}"|ALLOW
            validator says BLOCK because of continuity|BLOCK
            """
    )
    @DisplayName("""
        Given different validator payload shapes,
        When the validation decision is parsed,
        Then the allow or block decision should be recognized consistently
        """)
    void shouldParseValidationDecisionsAcrossDifferentPayloadShapes(String payload, String expectedDecision) {
        ValidationDecisionParser parser = new ValidationDecisionParser();

        assertEquals(expectedDecision, parser.parse(payload));
    }

    @Test
    @DisplayName("""
        Given a validator payload without a recognizable allow or block decision,
        When the validation decision is parsed,
        Then no decision should be returned
        """)
    void shouldReturnNullWhenValidationDecisionCannotBeParsed() {
        ValidationDecisionParser parser = new ValidationDecisionParser();

        assertNull(parser.parse("Maybe this is fine."));
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
