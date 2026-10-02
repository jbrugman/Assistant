package nl.llm.storyteller.db;

import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;

import java.util.Objects;

public record SessionSettings(
  SessionPrompts prompts,
  KnowledgeGraphDocument knowledgeGraph,
  Double temperature
) {
  public SessionSettings {
    Objects.requireNonNull(prompts, "prompts");
    Objects.requireNonNull(knowledgeGraph, "knowledgeGraph");
  }

  public SessionSettings(SessionPrompts prompts, KnowledgeGraphDocument knowledgeGraph) {
    this(prompts, knowledgeGraph, null);
  }
}
