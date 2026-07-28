package nl.llm.storyteller.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import nl.llm.storyteller.JsonSupport;
import nl.llm.storyteller.model.ValidationOutcome;

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
            ValidationOutcome structuredOutcome = parseStructuredNode(root, 0);
            return structuredOutcome != null ? structuredOutcome : parseFallback(normalizedPayload);
        } catch (JsonProcessingException _) {
            return parseFallback(normalizedPayload);
        }
    }

    private ValidationOutcome parseStructuredNode(JsonNode root, int depth) {
        if (root == null || depth > 3) {
            return null;
        }
        if (root.isTextual()) {
            return parseNestedText(root.asText());
        }

        String decision = root.path("decision").asText("").trim().toUpperCase();
        if (!decision.isEmpty()) {
            return new ValidationOutcome(decision, extractReplacementText(root));
        }

        ValidationOutcome nestedMessageOutcome = parseStructuredNode(root.path("message"), depth + 1);
        if (nestedMessageOutcome != null) {
            return nestedMessageOutcome;
        }

        ValidationOutcome nestedOutputOutcome = parseStructuredNode(root.path("output"), depth + 1);
        if (nestedOutputOutcome != null) {
            return nestedOutputOutcome;
        }

        ValidationOutcome nestedContentOutcome = parseNestedTextField(root, "content");
        if (nestedContentOutcome != null) {
            return nestedContentOutcome;
        }

        ValidationOutcome nestedTextOutcome = parseNestedTextField(root, "text");
        if (nestedTextOutcome != null) {
            return nestedTextOutcome;
        }

        String replacementText = extractReplacementText(root);
        if (!replacementText.isBlank()) {
            return new ValidationOutcome("REPLACE", replacementText);
        }
        return null;
    }

    private ValidationOutcome parseNestedTextField(JsonNode root, String fieldName) {
        JsonNode field = root.path(fieldName);
        if (!field.isTextual()) {
            return null;
        }
        return parseNestedText(field.asText());
    }

    private ValidationOutcome parseNestedText(String text) {
        String normalizedText = unwrapNestedJsonObject(text);
        try {
            JsonNode nestedRoot = JsonSupport.OBJECT_MAPPER.readTree(normalizedText);
            ValidationOutcome structuredOutcome = parseStructuredNode(nestedRoot, 1);
            if (structuredOutcome != null) {
                return structuredOutcome;
            }
        } catch (JsonProcessingException _) {
            // Fall back to tolerant plain-text parsing below.
        }
        return parseFallback(normalizedText);
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
        ValidationOutcome embeddedJsonOutcome = parseEmbeddedJsonOutcome(validationResult);
        if (embeddedJsonOutcome != null) {
            return embeddedJsonOutcome;
        }

        var decisionMatcher = VALIDATION_DECISION_PATTERN.matcher(validationResult);
        if (decisionMatcher.find()) {
            return new ValidationOutcome(decisionMatcher.group(1).toUpperCase(), "");
        }

        var plainMatcher = DECISION_ONLY_PATTERN.matcher(validationResult);
        if (plainMatcher.find()) {
            String decision = plainMatcher.group(1).toUpperCase();
            if ("REPLACE".equals(decision)) {
                String trailingReplacement = extractTrailingReplacementText(validationResult, plainMatcher.end());
                if (!trailingReplacement.isBlank()) {
                    return new ValidationOutcome(decision, trailingReplacement);
                }
            }
            return new ValidationOutcome(decision, "");
        }

        String trimmed = validationResult.trim();
        return trimmed.isBlank() ? null : new ValidationOutcome("REPLACE", trimmed);
    }

    private ValidationOutcome parseEmbeddedJsonOutcome(String validationResult) {
        int jsonStart = validationResult.indexOf('{');
        int jsonEnd = validationResult.lastIndexOf('}');
        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            return null;
        }

        String jsonCandidate = validationResult.substring(jsonStart, jsonEnd + 1).trim();
        try {
            JsonNode root = JsonSupport.OBJECT_MAPPER.readTree(jsonCandidate);
            if (root.isTextual()) {
                return null;
            }

            String decision = root.path("decision").asText("").trim().toUpperCase();
            String replacementText = extractReplacementText(root);
            if (!decision.isEmpty() || !replacementText.isBlank()) {
                return new ValidationOutcome(
                    decision.isEmpty() ? "REPLACE" : decision,
                    replacementText
                );
            }
            return null;
        } catch (JsonProcessingException _) {
            return null;
        }
    }

    private String extractTrailingReplacementText(String validationResult, int decisionEndIndex) {
        String trailingText = validationResult.substring(decisionEndIndex).trim();
        while (!trailingText.isEmpty() && startsWithSeparator(trailingText.charAt(0))) {
            trailingText = trailingText.substring(1).trim();
        }
        return trailingText;
    }

    private boolean startsWithSeparator(char character) {
        return character == ':' || character == '-' || character == '\n' || character == '\r';
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
