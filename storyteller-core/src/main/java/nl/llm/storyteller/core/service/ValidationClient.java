package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.model.Message;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ValidationClient {
  private final ChatClient client;
  private final nl.llm.storyteller.core.config.AppConfig config;
  private volatile boolean automaticStructuredOutputRejected;

  public ValidationClient(ChatClient client, nl.llm.storyteller.core.config.AppConfig config) {
    this.client = client;
    this.config = config;
  }

  public String validate(String validationSystemPrompt, String validationRequest) throws IOException, InterruptedException {
    List<Message> messages = List.of(
      new Message("system", validationSystemPrompt),
      new Message("user", validationRequest)
    );

    Map<String, Object> options = validationOptions();
    try {
      return client.chat(messages, options, config.validationRequestTimeoutSeconds());
    } catch (StructuredOutputNotSupportedException ex) {
      if (!usesAutomaticStructuredOutput(options)) {
        throw ex;
      }
      automaticStructuredOutputRejected = true;
      return client.chat(messages, config.validationOptions(), config.validationRequestTimeoutSeconds());
    }
  }

  private Map<String, Object> validationOptions() {
    if ("text".equalsIgnoreCase(config.validationOutputMode()) || automaticStructuredOutputRejected) {
      return config.validationOptions();
    }

    Map<String, Object> options = new LinkedHashMap<>(config.validationOptions());
    options.put("response_format", ValidationDecisionSchema.responseFormat());
    return Map.copyOf(options);
  }

  private boolean usesAutomaticStructuredOutput(Map<String, Object> options) {
    return "auto".equalsIgnoreCase(config.validationOutputMode())
      && options.containsKey("response_format");
  }
}
