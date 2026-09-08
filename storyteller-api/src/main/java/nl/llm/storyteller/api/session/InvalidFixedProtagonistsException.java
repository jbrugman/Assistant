package nl.llm.storyteller.api.session;

public final class InvalidFixedProtagonistsException extends IllegalArgumentException {
  private final int line;
  private final int column;

  InvalidFixedProtagonistsException(String message, int line, int column, Throwable cause) {
    super(message, cause);
    this.line = line;
    this.column = column;
  }

  public int line() {
    return line;
  }

  public int column() {
    return column;
  }
}
