package nl.llm.storyteller.core.graph.model;

import org.jetbrains.annotations.NotNull;

public record PredicateId(String value) {
  public PredicateId {
    value = value == null ? "" : value.trim();
  }

  @NotNull
  @Override
  public String toString() {
    return value;
  }
}
