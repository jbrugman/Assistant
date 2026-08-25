package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.model.HistoryState;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.model.SummaryPromptInput;

import java.util.ArrayList;
import java.util.List;

public final class SummaryManager extends DerivedMemoryManager {
  private final SummaryPromptBuilder summaryPromptBuilder;

  public SummaryManager(
    HistoryStore historyStore,
    ChatClient client,
    nl.llm.storyteller.core.config.AppConfig config,
    PromptResourceLoader promptResourceLoader,
    PromptTemplateService promptTemplateService,
    SummaryPromptBuilder summaryPromptBuilder
  ) {
    this(
      historyStore, client, config, promptResourceLoader, promptTemplateService, summaryPromptBuilder,
      new DerivedMemoryTaskQueue(), true
    );
  }

  public SummaryManager(
    HistoryStore historyStore,
    ChatClient client,
    nl.llm.storyteller.core.config.AppConfig config,
    PromptResourceLoader promptResourceLoader,
    PromptTemplateService promptTemplateService,
    SummaryPromptBuilder summaryPromptBuilder,
    DerivedMemoryTaskQueue taskQueue
  ) {
    this(historyStore, client, config, promptResourceLoader, promptTemplateService, summaryPromptBuilder, taskQueue, false);
  }

  private SummaryManager(
    HistoryStore historyStore,
    ChatClient client,
    nl.llm.storyteller.core.config.AppConfig config,
    PromptResourceLoader promptResourceLoader,
    PromptTemplateService promptTemplateService,
    SummaryPromptBuilder summaryPromptBuilder,
    DerivedMemoryTaskQueue taskQueue,
    boolean ownsTaskQueue
  ) {
    super(historyStore, client, config, promptResourceLoader, promptTemplateService, taskQueue, ownsTaskQueue);
    this.summaryPromptBuilder = summaryPromptBuilder;
  }

  public String loadSummary() {
    return loadMemory(config.summaryFile());
  }

  public void startUpdateSummaryIfNeeded() {
    triggerUpdateIfNeeded();
  }

  @Override
  protected boolean isDisabled() {
    return false;
  }

  @Override
  protected DerivedMemoryJob prepareJob() {
    HistoryState state = historyStore.load();
    List<Message> recent = historyStore.recentMessages(config.recentSummaryMaxTurns());
    int cutoffIndex = state.messages().size() - recent.size();
    int cursor = safeCursor(state.summaryCursor(), state.messages().size());

    if (cutoffIndex <= cursor) {
      return null;
    }

    List<Message> pendingMessages = new ArrayList<>(state.messages().subList(cursor, cutoffIndex));
    if (pendingMessages.size() < config.summaryBatchMessages()) {
      return null;
    }

    return new DerivedMemoryJob(cursor, cutoffIndex, loadSummary(), pendingMessages);
  }

  @Override
  protected List<Message> buildUpdateMessages(String existingContent, List<Message> pendingMessages) {
    return summaryPromptBuilder.build(new SummaryPromptInput(existingContent, formatHistory(pendingMessages)));
  }

  @Override
  protected int currentCursor(HistoryState state) {
    return state.summaryCursor();
  }

  @Override
  protected java.nio.file.Path targetFile() {
    return config.summaryFile();
  }

  @Override
  protected void markUpdated(int cutoffIndex) {
    historyStore.markSummarized(cutoffIndex);
  }

  @Override
  protected void ignoreFailure() {
    // Summary refresh is best-effort and must never interrupt the main chat flow.
  }
}
