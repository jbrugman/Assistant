package nl.llm.storyteller.api.session;

public final class InvalidKnowledgeGraphException extends IllegalArgumentException {
  public InvalidKnowledgeGraphException(String message, Throwable cause) {
    super(message, cause);
  }
}
