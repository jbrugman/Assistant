package nl.llm.storyteller.service;

import nl.llm.storyteller.AppConfig;
import nl.llm.storyteller.model.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StorySessionService {
    private static final String SYSTEM = "system";

    private final AppConfig config;
    private final HistoryStore historyStore;
    private final ChatClient chatClient;
    private final ResponseGuard responseGuard;
    private final SummaryManager summaryManager;
    private final RecentSummaryManager recentSummaryManager;
    private final CanonicalStateManager canonicalStateManager;
    private final PromptAssemblyService promptAssemblyService;
    private final PromptResourceLoader promptResourceLoader;

    public StorySessionService(
        AppConfig config,
        HistoryStore historyStore,
        ChatClient chatClient,
        ResponseGuard responseGuard,
        SummaryManager summaryManager,
        RecentSummaryManager recentSummaryManager,
        CanonicalStateManager canonicalStateManager,
        PromptAssemblyService promptAssemblyService,
        PromptResourceLoader promptResourceLoader
    ) {
        this.config = config;
        this.historyStore = historyStore;
        this.chatClient = chatClient;
        this.responseGuard = responseGuard;
        this.summaryManager = summaryManager;
        this.recentSummaryManager = recentSummaryManager;
        this.canonicalStateManager = canonicalStateManager;
        this.promptAssemblyService = promptAssemblyService;
        this.promptResourceLoader = promptResourceLoader;
    }

    public String handleUserTurn(String userInput) throws IOException, InterruptedException {
        if (isResetTurn(userInput)) {
            return handleControlTurn(userInput);
        }

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

    public UndoResult undoLastTurnAndReset() throws IOException, InterruptedException {
        String restoredUserInput = historyStore.removeLastTurn();
        if (restoredUserInput.isBlank()) {
            return new UndoResult("", "");
        }

        String resetResponse = handleControlTurn(config.resetStoryCommand());
        return new UndoResult(restoredUserInput, resetResponse);
    }

    public HistoryStore.LastTurn loadLastTurn() {
        return historyStore.loadLastTurn();
    }

    private boolean isResetTurn(String userInput) {
        return userInput != null
            && userInput.trim().equalsIgnoreCase(config.resetStoryCommand().trim());
    }

    private String handleControlTurn(String userInput) throws IOException, InterruptedException {
        List<Message> messages = withTransientResetCacheBuster(promptAssemblyService.buildResetMessages(userInput));
        String draftResponse = chatClient.chat(
            messages,
            config.chatOptions(),
            config.requestTimeoutSeconds()
        );
        return responseGuard.validate(
            promptAssemblyService.buildValidationSystemPrompt(),
            promptAssemblyService.buildValidationRequest(userInput, draftResponse),
            draftResponse
        );
    }

    private List<Message> withTransientResetCacheBuster(List<Message> messages) {
        if (messages.isEmpty() || !SYSTEM.equals(messages.getFirst().role())) {
            return messages;
        }

        List<Message> updatedMessages = new ArrayList<>(messages);
        Message firstMessage = updatedMessages.getFirst();
        String cacheBuster = promptResourceLoader.loadResetCacheBusterTemplate().formatted(UUID.randomUUID());
        updatedMessages.set(0, new Message(firstMessage.role(), firstMessage.content() + "\n\n" + cacheBuster.trim()));
        return List.copyOf(updatedMessages);
    }

    public record UndoResult(String restoredUserInput, String resetResponse) {
        public boolean hasRestoredUserInput() {
            return restoredUserInput != null && !restoredUserInput.isBlank();
        }
    }
}
