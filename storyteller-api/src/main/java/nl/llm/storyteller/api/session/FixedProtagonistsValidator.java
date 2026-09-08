package nl.llm.storyteller.api.session;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

final class FixedProtagonistsValidator {
  private static final String ROOT_PROPERTY = "fixed_protagonists";
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
    YAMLFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()
  );

  private FixedProtagonistsValidator() {
  }

  static void validate(String yaml) {
    try {
      JsonNode document = YAML_MAPPER.readTree(yaml);
      if (document == null || !document.isObject() || !document.path(ROOT_PROPERTY).isObject()) {
        throw new InvalidFixedProtagonistsException(
          "The YAML must contain a 'fixed_protagonists' mapping.", 1, 1, null
        );
      }
    } catch (JsonProcessingException ex) {
      JsonLocation location = ex.getLocation();
      throw new InvalidFixedProtagonistsException(
        ex.getOriginalMessage(),
        positive(location == null ? -1 : location.getLineNr()),
        positive(location == null ? -1 : location.getColumnNr()),
        ex
      );
    }
  }

  private static int positive(int value) {
    return Math.max(1, value);
  }
}
