package nl.llm.storyteller.core.graph.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record KnowledgeGraphDocument(
  int schemaVersion,
  long revision,
  Map<String, Entity> entities,
  List<Fact> facts
) {
  public static final int CURRENT_SCHEMA_VERSION = 1;

  public KnowledgeGraphDocument {
    entities = entities == null
      ? Map.of()
      : Collections.unmodifiableMap(new LinkedHashMap<>(entities));
    facts = facts == null ? List.of() : List.copyOf(facts);
  }

  public static KnowledgeGraphDocument empty() {
    return new KnowledgeGraphDocument(CURRENT_SCHEMA_VERSION, 0, Map.of(), List.of());
  }
}
