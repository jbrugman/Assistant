package nl.llm.storyteller.core.graph.turnbasedservice;

public interface KnowledgeGraphUpdateObserver {
  KnowledgeGraphUpdateObserver NONE = new KnowledgeGraphUpdateObserver() { };

  default void succeeded(int latestTurn, long revision, int entities, int facts) {
  }

  default void failed(int latestTurn, String reason) {
  }
}
