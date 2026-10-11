package nl.llm.storyteller.api.story;

public final class KnowledgeGraphConflictException extends RuntimeException {
  public KnowledgeGraphConflictException() {
    super("Knowledge graph revision changed. Reload the graph before replacing it.");
  }
}
