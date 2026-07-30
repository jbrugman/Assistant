package nl.llm.storyteller.service;

import nl.llm.storyteller.model.StoryChatPromptInput;
import nl.llm.storyteller.model.TurnRuleDecision;
import nl.llm.storyteller.model.ValidationPromptInput;
import nl.llm.storyteller.model.Message;

import java.util.List;

public final class PromptAssemblyService {
    private final HistoryStore historyStore;
    private final SummaryManager summaryManager;
    private final RecentSummaryManager recentSummaryManager;
    private final CanonicalStateManager canonicalStateManager;
    private final TurnManager turnManager;
    private final StoryChatPromptBuilder storyChatPromptBuilder;
    private final ValidationPromptBuilder validationPromptBuilder;

    public PromptAssemblyService(
        HistoryStore historyStore,
        SummaryManager summaryManager,
        RecentSummaryManager recentSummaryManager,
        CanonicalStateManager canonicalStateManager,
        TurnManager turnManager,
        StoryChatPromptBuilder storyChatPromptBuilder,
        ValidationPromptBuilder validationPromptBuilder
    ) {
        this.historyStore = historyStore;
        this.summaryManager = summaryManager;
        this.recentSummaryManager = recentSummaryManager;
        this.canonicalStateManager = canonicalStateManager;
        this.turnManager = turnManager;
        this.storyChatPromptBuilder = storyChatPromptBuilder;
        this.validationPromptBuilder = validationPromptBuilder;
    }

    public List<Message> buildChatMessages(String userInput) {
        TurnRuleDecision turnRuleDecision = turnManager.evaluate(userInput);
        return storyChatPromptBuilder.build(
            new StoryChatPromptInput(
                userInput,
                canonicalStateManager.loadCanonicalState(),
                summaryManager.loadSummary(),
                recentSummaryManager.loadRecentSummary(),
                historyStore.recentMessages(summaryManager.config.maxRecentTurns()),
                turnRuleDecision.promptInstruction()
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
