package nl.llm.storyteller.api.persistence;

public final class DatabaseException extends RuntimeException {
  public DatabaseException(String message, Throwable cause) {
    super(message, cause);
  }
}
