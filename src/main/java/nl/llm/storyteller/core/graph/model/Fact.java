package nl.llm.storyteller.core.graph.model;

public record Fact(
  String id,
  EntityId subject,
  Predicate predicate,
  EntityId object,
  Polarity polarity,
  FactStatus status,
  FactSource source,
  Integer sourceTurn,
  boolean hard
) {
  public Fact {
    id = id == null ? "" : id.trim();
  }
}
