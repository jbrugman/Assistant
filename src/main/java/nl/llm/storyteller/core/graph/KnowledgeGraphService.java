package nl.llm.storyteller.core.graph;

public interface KnowledgeGraphService {
  KnowledgeGraphSnapshot current();

  KnowledgeGraphSnapshot refresh();

  String relevantFacts(String text);
}
