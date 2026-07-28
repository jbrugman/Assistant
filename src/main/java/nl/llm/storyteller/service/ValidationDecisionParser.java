package nl.llm.storyteller.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import nl.llm.storyteller.JsonSupport;
import nl.llm.storyteller.model.ValidationOutcome;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

public final class ValidationDecisionParser {
    private static final String ALLOW = "ALLOW";
    private static final String REPLACE = "REPLACE";
    private static final String BLOCK = "BLOCK";
    private static final Pattern DECISION_ONLY_PATTERN = Pattern.compile(
        "\\b(ALLOW|REPLACE|BLOCK)\\b",
        Pattern.CASE_INSENSITIVE
    );

    public ValidationOutcome parse(String validationResult) {
        String normalizedPayload = unwrapNestedJsonObject(validationResult);
        if (normalizedPayload.isBlank()) {
            return null;
        }

        boolean replaceRequested = containsDecision(normalizedPayload, REPLACE);
        if (replaceRequested) {
            String firstResponse = extractFirstResponseFromRawPayload(normalizedPayload);
            if (!firstResponse.isBlank()) {
                return new ValidationOutcome(REPLACE, unwrapReplacementText(firstResponse));
            }
        }

        try {
            JsonNode root = JsonSupport.OBJECT_MAPPER.readTree(normalizedPayload);
            ValidationOutcome structuredOutcome = parseStructuredNode(root);
            if (structuredOutcome != null) {
                return structuredOutcome;
            }
        } catch (JsonProcessingException _) {
            // Fall through to tolerant text parsing.
        }

        if (containsDecision(normalizedPayload, ALLOW)) {
            return new ValidationOutcome(ALLOW, "");
        }
        if (containsDecision(normalizedPayload, BLOCK)) {
            return new ValidationOutcome(BLOCK, "");
        }
        if (replaceRequested) {
            String trailingReplacement = extractTrailingReplacement(normalizedPayload);
            if (!trailingReplacement.isBlank()) {
                return new ValidationOutcome(REPLACE, trailingReplacement);
            }
            return new ValidationOutcome(REPLACE, "");
        }

        return new ValidationOutcome(REPLACE, normalizedPayload.trim());
    }

    private ValidationOutcome parseStructuredNode(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }
        if (root.isTextual()) {
            return parse(root.asText());
        }
        if (root.isArray()) {
            Iterator<JsonNode> elements = root.elements();
            while (elements.hasNext()) {
                ValidationOutcome nestedOutcome = parseStructuredNode(elements.next());
                if (nestedOutcome != null) {
                    return nestedOutcome;
                }
            }
            return null;
        }

        String decision = root.path("decision").asText("").trim().toUpperCase();
        String firstResponse = findFirstResponseField(root);
        if (REPLACE.equals(decision) && !firstResponse.isBlank()) {
            return new ValidationOutcome(REPLACE, unwrapReplacementText(firstResponse));
        }
        if (ALLOW.equals(decision)) {
            return new ValidationOutcome(ALLOW, "");
        }
        if (BLOCK.equals(decision)) {
            return new ValidationOutcome(BLOCK, "");
        }
        if (!firstResponse.isBlank()) {
            return new ValidationOutcome(REPLACE, unwrapReplacementText(firstResponse));
        }

        ValidationOutcome nestedChoicesOutcome = parseStructuredNode(root.path("choices"));
        if (nestedChoicesOutcome != null) {
            return nestedChoicesOutcome;
        }

        ValidationOutcome nestedMessageOutcome = parseStructuredNode(root.path("message"));
        if (nestedMessageOutcome != null) {
            return nestedMessageOutcome;
        }

        ValidationOutcome nestedOutputOutcome = parseStructuredNode(root.path("output"));
        if (nestedOutputOutcome != null) {
            return nestedOutputOutcome;
        }

        JsonNode contentNode = root.path("content");
        if (contentNode.isTextual()) {
            ValidationOutcome nestedContentOutcome = parse(contentNode.asText());
            if (nestedContentOutcome != null) {
                return nestedContentOutcome;
            }
        }

        JsonNode textNode = root.path("text");
        if (textNode.isTextual()) {
            ValidationOutcome nestedTextOutcome = parse(textNode.asText());
            if (nestedTextOutcome != null) {
                return nestedTextOutcome;
            }
        }
        return null;
    }

    private boolean containsReplaceSignal(JsonNode root) {
        String serialized;
        try {
            serialized = JsonSupport.OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException _) {
            return false;
        }
        return containsDecision(serialized, REPLACE);
    }

    private String findFirstResponseField(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return "";
        }
        if (root.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if ("response".equals(field.getKey()) && field.getValue().isTextual()) {
                    return field.getValue().asText("");
                }
            }
            fields = root.fields();
            while (fields.hasNext()) {
                String nested = findFirstResponseField(fields.next().getValue());
                if (!nested.isBlank()) {
                    return nested;
                }
            }
            return "";
        }
        if (root.isArray()) {
            Iterator<JsonNode> elements = root.elements();
            while (elements.hasNext()) {
                String nested = findFirstResponseField(elements.next());
                if (!nested.isBlank()) {
                    return nested;
                }
            }
        }
        return "";
    }

    private String extractFirstResponseFromRawPayload(String rawPayload) {
        return extractQuotedFieldValue(rawPayload, "response");
    }

    private String extractQuotedFieldValue(String rawPayload, String fieldName) {
        String marker = "\"" + fieldName + "\"";
        int markerIndex = rawPayload.indexOf(marker);
        if (markerIndex < 0) {
            return "";
        }

        int colonIndex = rawPayload.indexOf(':', markerIndex + marker.length());
        if (colonIndex < 0) {
            return "";
        }

        int quoteIndex = rawPayload.indexOf('"', colonIndex + 1);
        if (quoteIndex < 0) {
            return "";
        }

        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int index = quoteIndex + 1; index < rawPayload.length(); index++) {
            char character = rawPayload.charAt(index);
            if (escaping) {
                value.append('\\').append(character);
                escaping = false;
                continue;
            }
            if (character == '\\') {
                escaping = true;
                continue;
            }
            if (character == '"') {
                return value.toString();
            }
            value.append(character);
        }
        return value.toString();
    }

    private String unwrapReplacementText(String replacementText) {
        String normalizedText = unwrapNestedJsonObject(replacementText).trim();
        if (normalizedText.isBlank()) {
            return normalizedText;
        }
        if (containsDecision(normalizedText, REPLACE)) {
            String nestedResponse = extractFirstResponseFromRawPayload(normalizedText);
            if (!nestedResponse.isBlank()) {
                return unwrapReplacementText(nestedResponse);
            }
            String trailingReplacement = extractTrailingReplacement(normalizedText);
            if (!trailingReplacement.isBlank()) {
                return trailingReplacement;
            }
        }
        return normalizedText;
    }

    private String extractTrailingReplacement(String payload) {
        var matcher = DECISION_ONLY_PATTERN.matcher(payload);
        if (!matcher.find() || !REPLACE.equals(matcher.group(1).toUpperCase())) {
            return "";
        }

        String trailingText = payload.substring(matcher.end()).trim();
        while (!trailingText.isEmpty() && startsWithSeparator(trailingText.charAt(0))) {
            trailingText = trailingText.substring(1).trim();
        }
        return trailingText;
    }

    private boolean startsWithSeparator(char character) {
        return character == ':' || character == '-' || character == '\n' || character == '\r';
    }

    private boolean containsDecision(String payload, String decision) {
        var matcher = DECISION_ONLY_PATTERN.matcher(payload);
        while (matcher.find()) {
            if (decision.equals(matcher.group(1).toUpperCase())) {
                return true;
            }
        }
        return false;
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
}
