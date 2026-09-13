package nl.llm.storyteller.core.service.openai;

import java.io.IOException;

public final class UnsupportedResponsesEndpointException extends IOException {
  public UnsupportedResponsesEndpointException(String message) {
    super(message);
  }
}
