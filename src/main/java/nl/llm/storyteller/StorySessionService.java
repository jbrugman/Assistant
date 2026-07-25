package nl.llm.storyteller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class StorySessionService {
    private static final String SYSTEM = "system";

    private final AppConfig config;
    private final HistoryStore historyStore;
    private final ChatClient chatClient;
    private final ResponseGuard responseGuard;
    private final SummaryManager summaryManager;
    private final RecentSummaryManager recentSummaryManager;
    private final CanonicalStateManager canonicalStateManager;
    private final PromptLoader promptLoader;

    StorySessionService(
        AppConfig config,
        HistoryStore historyStore,
        ChatClient chatClient,
        ResponseGuard responseGuard,
        SummaryManager summaryManager,
        RecentSummaryManager recentSummaryManager,
        CanonicalStateManager canonicalStateManager,
        PromptLoader promptLoader
    ) {
        this.config = config;
        this.historyStore = historyStore;
        this.chatClient = chatClient;
        this.responseGuard = responseGuard;
        this.summaryManager = summaryManager;
        this.recentSummaryManager = recentSummaryManager;
        this.canonicalStateManager = canonicalStateManager;
        this.promptLoader = promptLoader;
    }

    String handleUserTurn(String userInput) throws IOException, InterruptedException {
        List<Message> messages = buildChatMessages(userInput);
        String draftResponse = chatClient.chat(
            messages,
            config.chatOptions(),
            config.requestTimeoutSeconds()
        );
        String response = responseGuard.validate(
            promptLoader.loadValidationSystemPrompt(),
            promptLoader.loadValidationRequest(
                promptLoader.loadRulesPrompt(),
                promptLoader.loadFixedProtagonistsContext(),
                userInput,
                draftResponse
            ),
            draftResponse
        );

        historyStore.appendTurn(userInput, response);
        canonicalStateManager.startUpdateIfNeeded();
        recentSummaryManager.startUpdateIfNeeded();
        summaryManager.startUpdateSummaryIfNeeded();
        return response;
    }

    private List<Message> buildChatMessages(String userInput) {
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
}
