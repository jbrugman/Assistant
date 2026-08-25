package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.model.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StorySessionService {
  private static final String SYSTEM = "system";

  private final nl.llm.storyteller.core.config.AppConfig config;
  private final HistoryStore historyStore;
  private final ChatClient chatClient;
  private final ResponseGuard responseGuard;
  private final SummaryManager summaryManager;
  private final RecentSummaryManager recentSummaryManager;
  private final CanonicalStateManager canonicalStateManager;
  private final PromptAssemblyService promptAssemblyService;
  private final PromptResourceLoader promptResourceLoader;

  public StorySessionService(
    nl.llm.storyteller.core.config.AppConfig config,
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

    return handleStoryTurn(userInput, promptAssemblyService.buildChatMessages(userInput));
  }

  public String handleImageTurn(String userInput, String imageDataUrl) throws IOException, InterruptedException {
    List<Message> messages = new ArrayList<>(promptAssemblyService.buildChatMessages(userInput));
    Message userMessage = messages.getLast();
    messages.set(messages.size() - 1, Message.withImage(userMessage.role(), userMessage.content(), imageDataUrl));
    return handleStoryTurn(userInput, messages);
  }

  private String handleStoryTurn(String userInput, List<Message> messages) throws IOException, InterruptedException {
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
    runAutomaticCacheBusterIfDue();
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

  private void runAutomaticCacheBusterIfDue() {
    int interval = config.cacheBusterInterval();
    if (interval == 0 || historyStore.load().messages().size() / 2 % interval != 0) {
      return;
    }

    try {
      List<Message> messages = withTransientResetCacheBuster(
        promptAssemblyService.buildResetMessages(config.resetStoryCommand())
      );
      chatClient.chat(messages, config.chatOptions(), config.requestTimeoutSeconds());
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    } catch (IOException | RuntimeException _) {
      // A periodic cache-buster is best-effort and must not fail a completed story turn.
    }
  }

  private List<Message> withTransientResetCacheBuster(List<Message> messages) {
    if (messages.isEmpty() || !SYSTEM.equals(messages.getFirst().role())) {
      return messages;
    }

    List<Message> updatedMessages = new ArrayList<>(messages);
    Message firstMessage = updatedMessages.getFirst();
    String cacheBuster = promptResourceLoader.loadResetCacheBusterTemplate().formatted(UUID.randomUUID());
    updatedMessages.set(0, new Message(firstMessage.role(), cacheBuster.trim() + "\n\n" + firstMessage.content()));
    return List.copyOf(updatedMessages);
  }

  public record UndoResult(String restoredUserInput, String resetResponse) {
    public boolean hasRestoredUserInput() {
      return restoredUserInput != null && !restoredUserInput.isBlank();
    }
  }
}
