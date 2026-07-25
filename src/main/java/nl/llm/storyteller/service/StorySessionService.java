package nl.llm.storyteller.service;

import nl.llm.storyteller.AppConfig;
import nl.llm.storyteller.model.Message;

import java.io.IOException;
import java.util.List;

public final class StorySessionService {
    private final AppConfig config;
    private final HistoryStore historyStore;
    private final ChatClient chatClient;
    private final ResponseGuard responseGuard;
    private final SummaryManager summaryManager;
    private final RecentSummaryManager recentSummaryManager;
    private final CanonicalStateManager canonicalStateManager;
    private final PromptAssemblyService promptAssemblyService;

    public StorySessionService(
        AppConfig config,
        HistoryStore historyStore,
        ChatClient chatClient,
        ResponseGuard responseGuard,
        SummaryManager summaryManager,
        RecentSummaryManager recentSummaryManager,
        CanonicalStateManager canonicalStateManager,
        PromptAssemblyService promptAssemblyService
    ) {
        this.config = config;
        this.historyStore = historyStore;
        this.chatClient = chatClient;
        this.responseGuard = responseGuard;
        this.summaryManager = summaryManager;
        this.recentSummaryManager = recentSummaryManager;
        this.canonicalStateManager = canonicalStateManager;
        this.promptAssemblyService = promptAssemblyService;
    }

    public String handleUserTurn(String userInput) throws IOException, InterruptedException {
        List<Message> messages = promptAssemblyService.buildChatMessages(userInput);
        String draftResponse = chatClient.chat(
            messages,
            config.chatOptions(),
            config.requestTimeoutSeconds()
        );
        String response = responseGuard.validate(
            promptAssemblyService.buildValidationSystemPrompt(),
            promptAssemblyService.buildValidationRequest(userInput, draftResponse),
            draftResponse
        );

        historyStore.appendTurn(userInput, response);
        canonicalStateManager.startUpdateIfNeeded();
        recentSummaryManager.startUpdateIfNeeded();
        summaryManager.startUpdateSummaryIfNeeded();
        return response;
    }
}
