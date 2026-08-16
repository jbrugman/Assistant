package nl.llm.storyteller.core.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ValidationDecisionSchema {
  private ValidationDecisionSchema() {
  }

  static Map<String, Object> responseFormat() {
    return Map.of(
      "type", "json_schema",
      "json_schema", Map.of(
        "name", "validation_decision",
        "strict", true,
        "schema", schema()
      )
    );
  }

  private static Map<String, Object> schema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("decision", Map.of("type", "string", "enum", List.of("ALLOW", "REPLACE")));
    properties.put("response", Map.of("type", "string"));

    return Map.of(
      "type", "object",
      "additionalProperties", false,
      "properties", properties,
      "required", List.of("decision", "response")
    );
  }
}
