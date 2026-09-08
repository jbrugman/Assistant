package nl.llm.storyteller.api.story;

import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.model.CanonicalStatePromptInput;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.model.RecentSummaryPromptInput;
import nl.llm.storyteller.core.model.SummaryPromptInput;
import nl.llm.storyteller.core.service.CanonicalStatePromptBuilder;
import nl.llm.storyteller.core.service.ChatClient;
import nl.llm.storyteller.core.service.PromptResourceLoader;
import nl.llm.storyteller.core.service.PromptTemplateService;
import nl.llm.storyteller.core.service.RecentSummaryPromptBuilder;
import nl.llm.storyteller.core.service.SummaryPromptBuilder;
import nl.llm.storyteller.db.SessionMemory;
import nl.llm.storyteller.db.SessionMemoryRepository;
import nl.llm.storyteller.db.SessionSettingsRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SessionMemoryService implements AutoCloseable {
  private final SessionMemoryRepository memoryRepository;
  private final SessionSettingsRepository settingsRepository;
  private final AppConfig config;
  private final ChatClient client;
  private final SummaryPromptBuilder summaryPromptBuilder;
  private final RecentSummaryPromptBuilder recentSummaryPromptBuilder;
  private final CanonicalStatePromptBuilder canonicalStatePromptBuilder;
  private final ExecutorService executor;

  public SessionMemoryService(
    SessionMemoryRepository memoryRepository,
    SessionSettingsRepository settingsRepository,
    AppConfig config,
    ChatClient client
  ) {
    PromptResourceLoader resources = new PromptResourceLoader(config);
    PromptTemplateService templates = new PromptTemplateService(resources);
    this.memoryRepository = memoryRepository;
    this.settingsRepository = settingsRepository;
    this.config = config;
    this.client = client;
    this.summaryPromptBuilder = new SummaryPromptBuilder(resources, templates);
    this.recentSummaryPromptBuilder = new RecentSummaryPromptBuilder(resources, templates);
    this.canonicalStatePromptBuilder = new CanonicalStatePromptBuilder(resources, templates);
    this.executor = Executors.newSingleThreadExecutor(
      Thread.ofPlatform().daemon().name("storyteller-session-memory").factory()
    );
  }

  public void requestUpdate(String sessionId) {
    executor.submit(() -> refreshIgnoringFailure(sessionId));
  }

  void refresh(String sessionId) throws IOException, InterruptedException {
    updateSummary(sessionId);
    updateRecentSummary(sessionId);
    updateCanonicalState(sessionId);
  }

  private void updateSummary(String sessionId) throws IOException, InterruptedException {
    SessionMemory memory = memoryRepository.loadForUpdate(sessionId);
    List<Message> recent = recentTurns(memory.messages(), config.recentSummaryMaxTurns());
    int cursor = safeCursor(memory.summaryCursor(), memory.messages().size());
    int cutoff = memory.messages().size() - recent.size();
    if (cutoff <= cursor || cutoff - cursor < config.summaryBatchMessages()) {
      return;
    }
    List<Message> pending = memory.messages().subList(cursor, cutoff);
    String fixedProtagonists = settingsRepository.load(sessionId).prompts().fixedProtagonists();
    String content = chat(summaryPromptBuilder.build(
      new SummaryPromptInput(memory.summary(), formatHistory(pending)), fixedProtagonists
    ));
    memoryRepository.updateSummary(sessionId, memory, content, cutoff);
  }

  private void updateRecentSummary(String sessionId) throws IOException, InterruptedException {
    if (config.recentSummaryMaxTurns() <= config.maxRecentTurns()) {
      return;
    }
    SessionMemory memory = memoryRepository.loadForUpdate(sessionId);
    List<Message> trailing = recentTurns(memory.messages(), config.maxRecentTurns());
    int cursor = safeCursor(memory.recentSummaryCursor(), memory.messages().size());
    int cutoff = memory.messages().size() - trailing.size();
    if (cutoff <= cursor || cutoff - cursor < config.recentSummaryBatchMessages()) {
      return;
    }
    List<Message> completeWindow = recentTurns(memory.messages(), config.recentSummaryMaxTurns());
    List<Message> window = completeWindow.subList(0, Math.max(0, completeWindow.size() - trailing.size()));
    if (window.isEmpty()) {
      return;
    }
    String fixedProtagonists = settingsRepository.load(sessionId).prompts().fixedProtagonists();
    String content = chat(recentSummaryPromptBuilder.build(
      new RecentSummaryPromptInput(memory.recentSummary(), formatHistory(window)), fixedProtagonists
    ));
    memoryRepository.updateRecentSummary(sessionId, memory, content, cutoff);
  }

  private void updateCanonicalState(String sessionId) throws IOException, InterruptedException {
    SessionMemory memory = memoryRepository.loadForUpdate(sessionId);
    int cursor = safeCursor(memory.canonicalStateCursor(), memory.messages().size());
    int cutoff = memory.messages().size();
    if (cutoff <= cursor || cutoff - cursor < config.canonicalStateBatchMessages()) {
      return;
    }
    List<Message> pending = memory.messages().subList(cursor, cutoff);
    String fixedProtagonists = settingsRepository.load(sessionId).prompts().fixedProtagonists();
    String content = chat(canonicalStatePromptBuilder.build(
      new CanonicalStatePromptInput(memory.canonicalState(), formatHistory(pending)), fixedProtagonists
    ));
    memoryRepository.updateCanonicalState(sessionId, memory, content, cutoff);
  }

  private String chat(List<Message> messages) throws IOException, InterruptedException {
    return client.chat(messages, config.summaryOptions(), config.summaryRequestTimeoutSeconds());
  }

  private List<Message> recentTurns(List<Message> messages, int limitTurns) {
    if (limitTurns <= 0 || messages.isEmpty()) {
      return List.of();
    }
    int userMessages = 0;
    int start = messages.size();
    for (int index = messages.size() - 1; index >= 0; index--) {
      if ("user".equals(messages.get(index).role())) {
        userMessages++;
        start = index;
        if (userMessages >= limitTurns) {
          break;
        }
      }
    }
    return messages.subList(start, messages.size());
  }

  private int safeCursor(int cursor, int messageCount) {
    return Math.clamp(cursor, 0, messageCount);
  }

  private String formatHistory(List<Message> messages) {
    List<String> lines = new ArrayList<>(messages.size());
    for (Message message : messages) {
      lines.add(message.role().toUpperCase() + ": " + message.content());
    }
    return String.join("\n", lines);
  }

  private void refreshIgnoringFailure(String sessionId) {
    try {
      refresh(sessionId);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    } catch (IOException | RuntimeException _) {
      // Derived memory is best-effort and must never interrupt the main story flow.
    }
  }

  @Override
  public void close() {
    executor.close();
  }
}
