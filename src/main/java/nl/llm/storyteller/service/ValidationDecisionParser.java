package nl.llm.storyteller.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import nl.llm.storyteller.JsonSupport;

import java.util.regex.Pattern;

public final class ValidationDecisionParser {
    private static final Pattern VALIDATION_DECISION_PATTERN = Pattern.compile(
        "\"decision\"\\s*:\\s*\"(ALLOW|BLOCK)\"",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DECISION_ONLY_PATTERN = Pattern.compile(
        "\\b(ALLOW|BLOCK)\\b",
        Pattern.CASE_INSENSITIVE
    );

    public String parse(String validationResult) {
        String normalizedPayload = unwrapNestedJsonObject(validationResult);
        try {
            JsonNode root = JsonSupport.OBJECT_MAPPER.readTree(normalizedPayload);
            if (root.isTextual()) {
                return parseFallback(root.asText());
            }

            String decision = root.path("decision").asText("").trim().toUpperCase();
            return decision.isEmpty() ? null : decision;
        } catch (JsonProcessingException _) {
            return parseFallback(normalizedPayload);
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
            } catch (JsonProcessingException _) {
                return current;
            }
        }
        return current;
    }

    private String parseFallback(String validationResult) {
        var decisionMatcher = VALIDATION_DECISION_PATTERN.matcher(validationResult);
        if (decisionMatcher.find()) {
            return decisionMatcher.group(1).toUpperCase();
        }

        var plainMatcher = DECISION_ONLY_PATTERN.matcher(validationResult);
        return plainMatcher.find() ? plainMatcher.group(1).toUpperCase() : null;
    }
}
