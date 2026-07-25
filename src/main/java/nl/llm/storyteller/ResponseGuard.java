package nl.llm.storyteller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

final class ResponseGuard {
    private static final Pattern VALIDATION_DECISION_PATTERN = Pattern.compile(
        "\"decision\"\\s*:\\s*\"(ALLOW|BLOCK)\"",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DECISION_ONLY_PATTERN = Pattern.compile(
        "\\b(ALLOW|BLOCK)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private final ChatClient client;
    private final AppConfig config;

    ResponseGuard(ChatClient client, AppConfig config) {
        this.client = client;
        this.config = config;
    }

    String validate(String validationSystemPrompt, String validationRequest, String assistantResponse)
        throws IOException, InterruptedException {
        if (!config.validationEnabled()) {
            return sanitizeFinalResponse(assistantResponse);
        }

        if (assistantResponse == null || assistantResponse.isBlank()) {
            return config.validationFailClosedMessage();
        }

        List<Message> validationMessages = List.of(
            new Message(
                "system",
                validationSystemPrompt
            ),
            new Message(
                "user",
                validationRequest
            )
        );

        final String validatedResponse;
        try {
            validatedResponse = client.chat(
                validationMessages,
                config.validationOptions(),
                config.validationRequestTimeoutSeconds()
            );
        } catch (IOException | RuntimeException ex) {
            return config.validationFailClosedMessage();
        }

        if (validatedResponse == null || validatedResponse.isBlank()) {
            return config.validationFailClosedMessage();
        }

        return applyDecision(validatedResponse, assistantResponse);
    }

    private String applyDecision(String validationResult, String assistantResponse) {
        String decision = parseDecision(validationResult);
        if (decision == null) {
            return config.validationFailClosedMessage();
        }

        if ("ALLOW".equals(decision)) {
            return sanitizeFinalResponse(assistantResponse);
        }
        return config.validationFailClosedMessage();
    }

    private String parseDecision(String validationResult) {
        String normalizedPayload = unwrapNestedJsonObject(validationResult);
        try {
            JsonNode root = JsonSupport.OBJECT_MAPPER.readTree(normalizedPayload);
            if (root.isTextual()) {
                return parseDecisionFallback(root.asText());
            }

            String decision = root.path("decision").asText("").trim().toUpperCase();
            return decision.isEmpty() ? null : decision;
        } catch (JsonProcessingException ex) {
            return parseDecisionFallback(normalizedPayload);
        }
    }

    private String unwrapNestedJsonObject(String raw) {
        if (raw == null) {
            return "";
        }

        String current = raw.trim();
        for (int i = 0; i < 2; i++) {
            try {
                JsonNode node = JsonSupport.OBJECT_MAPPER.readTree(current);
                if (node.isTextual()) {
                    current = node.asText().trim();
                } else {
                    return current;
                }
            } catch (JsonProcessingException ex) {
                return current;
            }
        }
        return current;
    }

    private String parseDecisionFallback(String validationResult) {
        var decisionMatcher = VALIDATION_DECISION_PATTERN.matcher(validationResult);
        if (decisionMatcher.find()) {
            return decisionMatcher.group(1).toUpperCase();
        }

        var plainMatcher = DECISION_ONLY_PATTERN.matcher(validationResult);
        return plainMatcher.find() ? plainMatcher.group(1).toUpperCase() : null;
    }

    private String sanitizeFinalResponse(String response) {
        String normalized = response == null ? "" : response.trim();
        if (!normalized.contains("\\")) {
            return normalized;
        }

        String current = normalized;
        for (int i = 0; i < 3 && current.contains("\\"); i++) {
            String next = unescapeVisibleJsonEscapes(current);
            if (next.equals(current)) {
                return current;
            }
            current = next;
        }
        return current;
    }

    private String unescapeVisibleJsonEscapes(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean changed = false;

        int index = 0;
        while (index < value.length()) {
            char ch = value.charAt(index);
            int consumed = 1;

            if (ch == '\\' && index + 1 < value.length()) {
                char next = value.charAt(index + 1);
                Character replacement = switch (next) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> null;
                };

                if (replacement != null) {
                    result.append(replacement);
                    consumed = 2;
                    changed = true;
                } else {
                    result.append(ch);
                }
            } else {
                result.append(ch);
            }

            index += consumed;
        }

        return changed ? result.toString().trim() : value;
    }
}
