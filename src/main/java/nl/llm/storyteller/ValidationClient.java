package nl.llm.storyteller;

import java.io.IOException;
import java.util.List;

final class ValidationClient {
    private final ChatClient client;
    private final AppConfig config;

    ValidationClient(ChatClient client, AppConfig config) {
        this.client = client;
        this.config = config;
    }

    String validate(String validationSystemPrompt, String validationRequest) throws IOException, InterruptedException {
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
