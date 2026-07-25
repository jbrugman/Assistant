package nl.llm.storyteller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

interface ChatClient {
    String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds)
        throws IOException, InterruptedException;
}
