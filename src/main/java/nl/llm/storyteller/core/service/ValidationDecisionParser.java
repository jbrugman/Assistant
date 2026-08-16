package nl.llm.storyteller.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import nl.llm.storyteller.core.JsonSupport;
import nl.llm.storyteller.core.model.ValidationOutcome;

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

    ValidationOutcome structuredOutcome = parseJsonPayload(normalizedPayload);
    if (structuredOutcome != null) {
      return structuredOutcome;
    }

    return parseTolerantText(normalizedPayload);
  }

  private ValidationOutcome parseJsonPayload(String payload) {
    try {
      return parseStructuredNode(JsonSupport.OBJECT_MAPPER.readTree(payload));
    } catch (JsonProcessingException _) {
      return null;
    }
  }

  private ValidationOutcome parseTolerantText(String payload) {
    boolean replaceRequested = containsDecision(payload, REPLACE);
    if (replaceRequested) {
      String firstResponse = extractFirstResponseFromRawPayload(payload);
      if (!firstResponse.isBlank()) {
        return new ValidationOutcome(REPLACE, unwrapReplacementText(firstResponse));
      }
    }

    if (containsDecision(payload, ALLOW)) {
      return new ValidationOutcome(ALLOW, "");
    }
    if (containsDecision(payload, BLOCK)) {
      return new ValidationOutcome(BLOCK, "");
    }
    if (replaceRequested) {
      String trailingReplacement = extractTrailingReplacement(payload);
      if (!trailingReplacement.isBlank()) {
        return new ValidationOutcome(REPLACE, trailingReplacement);
      }
      return new ValidationOutcome(REPLACE, "");
    }

    return new ValidationOutcome(REPLACE, payload.trim());
  }

  private ValidationOutcome parseStructuredNode(JsonNode root) {
    if (root == null || root.isMissingNode() || root.isNull()) {
      return null;
    }
    if (root.isTextual()) {
      return parse(root.asText());
    }
    if (root.isArray()) {
      return parseArrayNode(root);
    }

    return parseObjectNode(root);
  }

  private ValidationOutcome parseArrayNode(JsonNode root) {
    for (JsonNode element : root) {
      ValidationOutcome nestedOutcome = parseStructuredNode(element);
      if (nestedOutcome != null) {
        return nestedOutcome;
      }
    }
    return null;
  }

  private ValidationOutcome parseObjectNode(JsonNode root) {
    String decision = root.path("decision").asText("").trim();
    String firstResponse = findFirstResponseField(root);
    ValidationOutcome decisionOutcome = parseExplicitDecision(decision, firstResponse);
    if (decisionOutcome != null) {
      return decisionOutcome;
    }
    if (!firstResponse.isBlank()) {
      return new ValidationOutcome(REPLACE, unwrapReplacementText(firstResponse));
    }

    return parseNestedFields(root);
  }

  private ValidationOutcome parseExplicitDecision(String decision, String firstResponse) {
    if (REPLACE.equalsIgnoreCase(decision) && !firstResponse.isBlank()) {
      return new ValidationOutcome(REPLACE, unwrapReplacementText(firstResponse));
    }
    if (ALLOW.equalsIgnoreCase(decision)) {
      return new ValidationOutcome(ALLOW, "");
    }
    if (BLOCK.equalsIgnoreCase(decision)) {
      return new ValidationOutcome(BLOCK, "");
    }
    return null;
  }

  private ValidationOutcome parseNestedFields(JsonNode root) {
    for (String fieldName : new String[] {"choices", "message", "output"}) {
      ValidationOutcome nestedOutcome = parseStructuredNode(root.path(fieldName));
      if (nestedOutcome != null) {
        return nestedOutcome;
      }
    }

    ValidationOutcome contentOutcome = parseTextField(root.path("content"));
    return contentOutcome != null ? contentOutcome : parseTextField(root.path("text"));
  }

  private ValidationOutcome parseTextField(JsonNode node) {
    return node.isTextual() ? parse(node.asText()) : null;
  }

  private String findFirstResponseField(JsonNode root) {
    if (root == null || root.isMissingNode() || root.isNull()) {
      return "";
    }
    String directResponse = extractDirectResponse(root);
    return directResponse.isBlank() ? findResponseInChildren(root) : directResponse;
  }

  private String extractDirectResponse(JsonNode root) {
    JsonNode responseNode = root.path("response");
    return responseNode.isTextual() ? responseNode.asText("") : "";
  }

  private String findResponseInChildren(JsonNode root) {
    for (JsonNode child : root) {
      String nestedResponse = findFirstResponseField(child);
      if (!nestedResponse.isBlank()) {
        return nestedResponse;
      }
    }
    return "";
  }

  private String extractFirstResponseFromRawPayload(String rawPayload) {
    String marker = "\"response\"";
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
      } else if (character == '\\') {
        escaping = true;
      } else if (character == '"') {
        return value.toString();
      } else {
        value.append(character);
      }
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
    if (!matcher.find() || !REPLACE.equalsIgnoreCase(matcher.group(1))) {
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
      if (decision.equalsIgnoreCase(matcher.group(1))) {
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
