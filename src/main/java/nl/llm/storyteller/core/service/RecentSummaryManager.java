package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.AppConfig;
import nl.llm.storyteller.core.model.HistoryState;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.model.RecentSummaryPromptInput;

import java.util.List;

public final class RecentSummaryManager extends DerivedMemoryManager {
  private final RecentSummaryPromptBuilder recentSummaryPromptBuilder;

  public RecentSummaryManager(
    HistoryStore historyStore,
    ChatClient client,
    AppConfig config,
    PromptResourceLoader promptResourceLoader,
    PromptTemplateService promptTemplateService,
    RecentSummaryPromptBuilder recentSummaryPromptBuilder
  ) {
    this(
      historyStore, client, config, promptResourceLoader, promptTemplateService, recentSummaryPromptBuilder,
      new DerivedMemoryTaskQueue(), true
    );
  }

  public RecentSummaryManager(
    HistoryStore historyStore,
    ChatClient client,
    AppConfig config,
    PromptResourceLoader promptResourceLoader,
    PromptTemplateService promptTemplateService,
    RecentSummaryPromptBuilder recentSummaryPromptBuilder,
    DerivedMemoryTaskQueue taskQueue
  ) {
    this(historyStore, client, config, promptResourceLoader, promptTemplateService, recentSummaryPromptBuilder, taskQueue, false);
  }

  private RecentSummaryManager(
    HistoryStore historyStore,
    ChatClient client,
    AppConfig config,
    PromptResourceLoader promptResourceLoader,
    PromptTemplateService promptTemplateService,
    RecentSummaryPromptBuilder recentSummaryPromptBuilder,
    DerivedMemoryTaskQueue taskQueue,
    boolean ownsTaskQueue
  ) {
    super(historyStore, client, config, promptResourceLoader, promptTemplateService, taskQueue, ownsTaskQueue);
    this.recentSummaryPromptBuilder = recentSummaryPromptBuilder;
  }

  public String loadRecentSummary() {
    return loadMemory(config.recentSummaryFile());
  }

  public void startUpdateIfNeeded() {
    triggerUpdateIfNeeded();
  }

  @Override
  protected boolean isDisabled() {
    return config.recentSummaryMaxTurns() <= config.maxRecentTurns();
  }

  @Override
  protected DerivedMemoryJob prepareJob() {
    HistoryState state = historyStore.load();
    List<Message> recent = historyStore.recentMessages(config.maxRecentTurns());
    int cutoffIndex = state.messages().size() - recent.size();
    int cursor = safeCursor(state.recentSummaryCursor(), state.messages().size());

    if (cutoffIndex <= cursor) {
      return null;
    }

    int pendingMessagesCount = cutoffIndex - cursor;
    if (pendingMessagesCount < config.recentSummaryBatchMessages()) {
      return null;
    }

    List<Message> windowMessages = historyStore.recentMessagesWindow(
      config.recentSummaryMaxTurns(),
      config.maxRecentTurns()
    );
    if (windowMessages.isEmpty()) {
      return null;
    }

    return new DerivedMemoryJob(cursor, cutoffIndex, loadRecentSummary(), windowMessages);
  }

  @Override
  protected List<Message> buildUpdateMessages(String existingContent, List<Message> pendingMessages) {
    return recentSummaryPromptBuilder.build(
      new RecentSummaryPromptInput(existingContent, formatHistory(pendingMessages))
    );
  }

  @Override
  protected int currentCursor(HistoryState state) {
    return state.recentSummaryCursor();
  }

  @Override
  protected java.nio.file.Path targetFile() {
    return config.recentSummaryFile();
  }

  @Override
  protected void markUpdated(int cutoffIndex) {
    historyStore.markRecentSummarized(cutoffIndex);
  }

  @Override
  protected void ignoreFailure() {
    // Recent summary refresh is best-effort and must never interrupt the main chat flow.
  }
}
