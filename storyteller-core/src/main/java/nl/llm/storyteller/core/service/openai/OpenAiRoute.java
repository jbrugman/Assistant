package nl.llm.storyteller.core.service.openai;

import nl.llm.storyteller.core.model.Message;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface OpenAiRoute {
  OpenAiRouteResult execute(List<Message> messages, Map<String, Object> options, int timeoutSeconds)
    throws IOException, InterruptedException;
}
