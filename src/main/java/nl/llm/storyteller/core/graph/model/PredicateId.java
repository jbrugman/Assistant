package nl.llm.storyteller.core.graph.model;

public record PredicateId(String value) {
  public PredicateId {
    value = value == null ? "" : value.trim();
  }

  @Override
  public String toString() {
    return value;
  }
}
