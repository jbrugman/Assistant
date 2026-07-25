package nl.llm.storyteller.service;

import nl.llm.storyteller.model.Message;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ChatClient {
    String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds)
        throws IOException, InterruptedException;
}
