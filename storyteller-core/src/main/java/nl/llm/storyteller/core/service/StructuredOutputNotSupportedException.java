package nl.llm.storyteller.core.service;

import java.io.IOException;

public final class StructuredOutputNotSupportedException extends IOException {
  public StructuredOutputNotSupportedException(String message) {
    super(message);
  }
}
