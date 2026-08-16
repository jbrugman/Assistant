package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.AppConfig;
import nl.llm.storyteller.core.model.Message;

import java.io.IOException;
import java.util.List;

public final class ValidationClient {
  private final ChatClient client;
  private final AppConfig config;

  public ValidationClient(ChatClient client, AppConfig config) {
    this.client = client;
    this.config = config;
  }

  public String validate(String validationSystemPrompt, String validationRequest) throws IOException, InterruptedException {
    return client.chat(
      List.of(
        new Message("system", validationSystemPrompt),
        new Message("user", validationRequest)
      ),
      config.validationOptions(),
      config.validationRequestTimeoutSeconds()
    );
  }
}
