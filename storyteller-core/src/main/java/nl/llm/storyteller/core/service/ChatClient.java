package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.Message;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ChatClient {
  String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds)
    throws IOException, InterruptedException;

  default String chat(String purpose, List<Message> messages, Map<String, Object> options, int timeoutSeconds)
    throws IOException, InterruptedException {
    return chat(messages, options, timeoutSeconds);
  }
}
