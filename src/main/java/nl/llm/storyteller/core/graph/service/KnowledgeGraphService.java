package nl.llm.storyteller.core.graph.service;

import nl.llm.storyteller.core.graph.KnowledgeGraphSnapshot;

public interface KnowledgeGraphService {
  KnowledgeGraphSnapshot current();

  KnowledgeGraphSnapshot refresh();

  String relevantFacts(String text);
}
