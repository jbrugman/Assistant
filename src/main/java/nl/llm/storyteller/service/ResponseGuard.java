package nl.llm.storyteller.service;

import nl.llm.storyteller.AppConfig;

import java.io.IOException;

public final class ResponseGuard {
    private final AppConfig config;
    private final ValidationClient validationClient;
    private final ValidationDecisionParser validationDecisionParser;
    private final ResponseSanitizer responseSanitizer;

    public ResponseGuard(ChatClient client, AppConfig config) {
        this(
            config,
            new ValidationClient(client, config),
            new ValidationDecisionParser(),
            new ResponseSanitizer()
        );
    }

    public ResponseGuard(
        AppConfig config,
        ValidationClient validationClient,
        ValidationDecisionParser validationDecisionParser,
        ResponseSanitizer responseSanitizer
    ) {
        this.config = config;
        this.validationClient = validationClient;
        this.validationDecisionParser = validationDecisionParser;
        this.responseSanitizer = responseSanitizer;
    }

    public String validate(String validationSystemPrompt, String validationRequest, String assistantResponse)
        throws IOException, InterruptedException {
        if (!config.validationEnabled()) {
            return responseSanitizer.sanitize(assistantResponse);
        }

        if (assistantResponse == null || assistantResponse.isBlank()) {
            return config.validationFailClosedMessage();
        }

        final String validatedResponse;
        try {
            validatedResponse = validationClient.validate(validationSystemPrompt, validationRequest);
        } catch (IOException | RuntimeException ex) {
            return config.validationFailClosedMessage();
        }

        if (validatedResponse == null || validatedResponse.isBlank()) {
            return config.validationFailClosedMessage();
        }

        return applyDecision(validatedResponse, assistantResponse);
    }

    private String applyDecision(String validationResult, String assistantResponse) {
        String decision = validationDecisionParser.parse(validationResult);
        if (decision == null) {
            return config.validationFailClosedMessage();
        }

        if ("ALLOW".equals(decision)) {
            return responseSanitizer.sanitize(assistantResponse);
        }
        return config.validationFailClosedMessage();
    }
}
