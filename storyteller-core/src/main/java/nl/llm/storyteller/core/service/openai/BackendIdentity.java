package nl.llm.storyteller.core.service.openai;

import java.io.IOException;

@FunctionalInterface
public interface BackendIdentity {
  boolean isOmlx() throws IOException, InterruptedException;
}
