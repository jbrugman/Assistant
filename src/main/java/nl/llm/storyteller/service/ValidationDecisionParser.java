package nl.llm.storyteller.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import nl.llm.storyteller.JsonSupport;

import java.util.regex.Pattern;

public final class ValidationDecisionParser {
    private static final Pattern VALIDATION_DECISION_PATTERN = Pattern.compile(
        "\"decision\"\\s*:\\s*\"(ALLOW|REPLACE|BLOCK)\"",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DECISION_ONLY_PATTERN = Pattern.compile(
        "\\b(ALLOW|REPLACE|BLOCK)\\b",
        Pattern.CASE_INSENSITIVE
    );

    public ValidationOutcome parse(String validationResult) {
        String normalizedPayload = unwrapNestedJsonObject(validationResult);
        try {
            JsonNode root = JsonSupport.OBJECT_MAPPER.readTree(normalizedPayload);
            if (root.isTextual()) {
                return parseFallback(root.asText());
            }

            String decision = root.path("decision").asText("").trim().toUpperCase();
            if (!decision.isEmpty()) {
                return new ValidationOutcome(decision, extractReplacementText(root));
            }

            String replacementText = extractReplacementText(root);
            if (!replacementText.isBlank()) {
                return new ValidationOutcome("REPLACE", replacementText);
            }
            return parseFallback(normalizedPayload);
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

    private ValidationOutcome parseFallback(String validationResult) {
        var decisionMatcher = VALIDATION_DECISION_PATTERN.matcher(validationResult);
        if (decisionMatcher.find()) {
            return new ValidationOutcome(decisionMatcher.group(1).toUpperCase(), "");
        }

        var plainMatcher = DECISION_ONLY_PATTERN.matcher(validationResult);
        if (plainMatcher.find()) {
            return new ValidationOutcome(plainMatcher.group(1).toUpperCase(), "");
        }

        String trimmed = validationResult == null ? "" : validationResult.trim();
        return trimmed.isBlank() ? null : new ValidationOutcome("REPLACE", trimmed);
    }

    private String extractReplacementText(JsonNode root) {
        String replacementText = root.path("response").asText("");
        if (replacementText.isBlank()) {
            replacementText = root.path("replacement_text").asText("");
        }
        if (replacementText.isBlank()) {
            replacementText = root.path("replacement").asText("");
        }
        if (replacementText.isBlank()) {
            replacementText = root.path("text").asText("");
        }
        if (replacementText.isBlank()) {
            replacementText = root.path("content").asText("");
        }
        return replacementText;
    }
}
