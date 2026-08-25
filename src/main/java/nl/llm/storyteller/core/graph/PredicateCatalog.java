package nl.llm.storyteller.core.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import nl.llm.storyteller.core.JsonSupport;
import nl.llm.storyteller.core.graph.model.EntityType;
import nl.llm.storyteller.core.graph.model.PredicateId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import nl.llm.storyteller.core.FileSupport;
import java.nio.file.Path;

public final class PredicateCatalog {
  private final Map<PredicateId, PredicateDefinition> definitions;

  private PredicateCatalog(Map<PredicateId, PredicateDefinition> definitions) {
    this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
  }

  public static PredicateCatalog fromJson(String json) {
    try {
      JsonNode root = JsonSupport.OBJECT_MAPPER.readTree(json);
      Map<PredicateId, PredicateDefinition> definitions = new LinkedHashMap<>();
      root.path("predicates").fields().forEachRemaining(entry -> {
        PredicateId id = new PredicateId(entry.getKey());
        JsonNode node = entry.getValue();
        definitions.put(id, new PredicateDefinition(
          id,
          EntityType.valueOf(node.path("subjectType").asText()),
          EntityType.valueOf(node.path("objectType").asText()),
          node.path("temporal").asBoolean(false),
          requiredText(node, "positiveText", id),
          requiredText(node, "negativeText", id)
        ));
      });
      if (definitions.isEmpty()) {
        throw new IllegalArgumentException("Predicate catalog must define at least one predicate.");
      }
      return new PredicateCatalog(definitions);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Invalid predicate catalog JSON: " + ex.getOriginalMessage(), ex);
    }
  }

  public static PredicateCatalog load(Path baseDir) {
    Path path = baseDir.resolve("systemprompts/graph-predicates.json");
    return fromJson(FileSupport.readRequiredTextFileOrResource(path, baseDir));
  }

  public Optional<PredicateDefinition> find(PredicateId id) {
    return Optional.ofNullable(definitions.get(id));
  }

  public PredicateDefinition require(PredicateId id) {
    return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown graph predicate: " + id));
  }

  public String modelInstructions() {
    return definitions.values().stream()
      .map(definition -> definition.id() + " (" + definition.subjectType() + " to " + definition.objectType() + ")")
      .collect(Collectors.joining(", "));
  }

  private static String requiredText(JsonNode node, String field, PredicateId id) {
    String value = node.path(field).asText("").trim();
    if (value.isBlank()) {
      throw new IllegalArgumentException("Predicate " + id + " requires " + field + ".");
    }
    return value;
  }
}
