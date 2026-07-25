package nl.llm.storyteller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private record FakeChatClient(String response) implements ChatClient {
        @Override
        public String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds) throws IOException {
            return response;
        }
    }
}
