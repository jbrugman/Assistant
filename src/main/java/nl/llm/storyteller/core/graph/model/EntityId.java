package nl.llm.storyteller.core.graph.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jetbrains.annotations.NotNull;

public record EntityId(String value) implements Comparable<EntityId> {
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public EntityId {
    value = value == null ? "" : value.trim();
  }

  @JsonValue
  @Override
  public String value() {
    return value;
  }

  @Override
  public int compareTo(EntityId other) {
    return value.compareTo(other.value);
  }

  @NotNull
  @Override
  public String toString() {
    return value;
  }
}
