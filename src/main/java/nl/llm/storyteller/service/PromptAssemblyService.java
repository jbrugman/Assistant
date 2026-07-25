package nl.llm.storyteller.service;

import nl.llm.storyteller.AppConfig;
import nl.llm.storyteller.model.Message;

import java.util.ArrayList;
import java.util.List;

public final class PromptAssemblyService {
    private static final String SYSTEM = "system";

    private final AppConfig config;
    private final HistoryStore historyStore;
    private final SummaryManager summaryManager;
    private final RecentSummaryManager recentSummaryManager;
    private final CanonicalStateManager canonicalStateManager;
    private final PromptLoader promptLoader;

    public PromptAssemblyService(
        AppConfig config,
        HistoryStore historyStore,
        SummaryManager summaryManager,
        RecentSummaryManager recentSummaryManager,
        CanonicalStateManager canonicalStateManager,
        PromptLoader promptLoader
    ) {
        this.config = config;
        this.historyStore = historyStore;
        this.summaryManager = summaryManager;
        this.recentSummaryManager = recentSummaryManager;
        this.canonicalStateManager = canonicalStateManager;
        this.promptLoader = promptLoader;
    }

    public List<Message> buildChatMessages(String userInput) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(SYSTEM, promptLoader.loadSystemPrompt()));

        String fixedProtagonists = promptLoader.loadFixedProtagonistsContext();
        if (!fixedProtagonists.isBlank()) {
            messages.add(new Message(SYSTEM, fixedProtagonists));
        }

        String canonicalState = canonicalStateManager.loadCanonicalState();
        if (!canonicalState.isBlank()) {
            messages.add(new Message(SYSTEM, promptLoader.loadCanonicalStateContext(canonicalState)));
        }

        String summary = summaryManager.loadSummary();
        if (!summary.isBlank()) {
            messages.add(new Message(SYSTEM, promptLoader.loadSummaryContext(summary)));
        }

        String recentSummary = recentSummaryManager.loadRecentSummary();
        if (!recentSummary.isBlank()) {
            messages.add(new Message(SYSTEM, promptLoader.loadRecentSummaryContext(recentSummary)));
        }

        messages.addAll(historyStore.recentMessages(config.maxRecentTurns()));
        messages.add(new Message("user", userInput));
        return messages;
    }

    public String buildValidationSystemPrompt() {
        return promptLoader.loadValidationSystemPrompt();
    }

    public String buildValidationRequest(String userInput, String draftResponse) {
        return promptLoader.loadValidationRequest(
            promptLoader.loadRulesPrompt(),
            promptLoader.loadFixedProtagonistsContext(),
            userInput,
            draftResponse
        );
    }
}
