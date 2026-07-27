package nl.llm.storyteller.service;

import nl.llm.storyteller.model.Message;
import nl.llm.storyteller.model.StoryChatPromptInput;

import java.util.ArrayList;
import java.util.List;

public final class StoryChatPromptBuilder {
    private static final String SYSTEM = "system";
    private static final String USER = "user";

    private final PromptResourceLoader promptResourceLoader;
    private final PromptTemplateService promptTemplateService;

    public StoryChatPromptBuilder(
        PromptResourceLoader promptResourceLoader,
        PromptTemplateService promptTemplateService
    ) {
        this.promptResourceLoader = promptResourceLoader;
        this.promptTemplateService = promptTemplateService;
    }

    public List<Message> build(StoryChatPromptInput input) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(SYSTEM, promptResourceLoader.loadSystemPrompt()));

        addIfPresent(messages, promptTemplateService.buildFixedProtagonistsContext());
        addIfPresent(messages, promptTemplateService.buildCanonicalStateContext(input.canonicalState()));
        addIfPresent(messages, promptTemplateService.buildSummaryContext(input.summary()));
        addIfPresent(messages, promptTemplateService.buildRecentSummaryContext(input.recentSummary()));

        messages.addAll(input.recentMessages());
        messages.add(new Message(USER, input.userInput()));
        return messages;
    }

    private void addIfPresent(List<Message> messages, String content) {
        if (!content.isBlank()) {
            messages.add(new Message(SYSTEM, content));
        }
    }
}
