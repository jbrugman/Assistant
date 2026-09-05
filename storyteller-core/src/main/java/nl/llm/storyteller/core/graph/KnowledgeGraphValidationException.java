package nl.llm.storyteller.core.graph;

import java.util.List;

public final class KnowledgeGraphValidationException extends IllegalArgumentException {
  private final List<String> violations;

  public KnowledgeGraphValidationException(List<String> violations) {
    super("Invalid knowledge graph: " + String.join("; ", violations));
    this.violations = List.copyOf(violations);
  }

  public List<String> violations() {
    return violations;
  }
}
