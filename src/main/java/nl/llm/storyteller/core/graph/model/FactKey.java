package nl.llm.storyteller.core.graph.model;

public record FactKey(
  EntityId subject,
  PredicateId predicate,
  EntityId object
) { }
