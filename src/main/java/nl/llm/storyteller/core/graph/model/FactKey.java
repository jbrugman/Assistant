package nl.llm.storyteller.core.graph.model;

public record FactKey(
  EntityId subject,
  Predicate predicate,
  EntityId object
) {
}
