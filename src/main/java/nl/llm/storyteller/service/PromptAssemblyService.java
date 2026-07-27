package nl.llm.storyteller.service;

import nl.llm.storyteller.model.StoryChatPromptInput;
import nl.llm.storyteller.model.ValidationPromptInput;
import nl.llm.storyteller.model.Message;

import java.util.List;

public final class PromptAssemblyService {
    private final HistoryStore historyStore;
    private final SummaryManager summaryManager;
    private final RecentSummaryManager recentSummaryManager;
    private final CanonicalStateManager canonicalStateManager;
    private final StoryChatPromptBuilder storyChatPromptBuilder;
    private final ValidationPromptBuilder validationPromptBuilder;

    public PromptAssemblyService(
        HistoryStore historyStore,
        SummaryManager summaryManager,
        RecentSummaryManager recentSummaryManager,
        CanonicalStateManager canonicalStateManager,
        StoryChatPromptBuilder storyChatPromptBuilder,
        ValidationPromptBuilder validationPromptBuilder
    ) {
        this.historyStore = historyStore;
        this.summaryManager = summaryManager;
        this.recentSummaryManager = recentSummaryManager;
        this.canonicalStateManager = canonicalStateManager;
        this.storyChatPromptBuilder = storyChatPromptBuilder;
        this.validationPromptBuilder = validationPromptBuilder;
    }

    public List<Message> buildChatMessages(String userInput) {
        return storyChatPromptBuilder.build(
            new StoryChatPromptInput(
                userInput,
                canonicalStateManager.loadCanonicalState(),
                summaryManager.loadSummary(),
                recentSummaryManager.loadRecentSummary(),
                historyStore.recentMessages(summaryManager.config.maxRecentTurns())
            )
        );
    }

    public String buildValidationSystemPrompt() {
        return validationPromptBuilder.buildSystemPrompt();
    }

    public String buildValidationRequest(String userInput, String draftResponse) {
        return validationPromptBuilder.buildRequest(new ValidationPromptInput(userInput, draftResponse));
    }
}
