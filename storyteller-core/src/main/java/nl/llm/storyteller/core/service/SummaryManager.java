package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.HistoryState;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.model.SummaryPromptInput;

import java.util.ArrayList;
import java.util.List;

public final class SummaryManager extends DerivedMemoryManager {
  private final SummaryPromptBuilder summaryPromptBuilder;

  public SummaryManager(
    StoryHistory historyStore,
    ChatClient client,
    nl.llm.storyteller.core.config.AppConfig config,
    PromptLoader promptResourceLoader,
    PromptTemplateService promptTemplateService,
    SummaryPromptBuilder summaryPromptBuilder
  ) {
    this(
      historyStore, client, config, promptResourceLoader, promptTemplateService, summaryPromptBuilder,
      new FileTextMemory(config.summaryFile()), new DerivedMemoryTaskQueue(), true
    );
  }

  public SummaryManager(
    StoryHistory historyStore,
    ChatClient client,
    nl.llm.storyteller.core.config.AppConfig config,
    PromptLoader promptResourceLoader,
    PromptTemplateService promptTemplateService,
    SummaryPromptBuilder summaryPromptBuilder,
    DerivedMemoryTaskQueue taskQueue
  ) {
    this(
      historyStore, client, config, promptResourceLoader, promptTemplateService, summaryPromptBuilder,
      new FileTextMemory(config.summaryFile()), taskQueue, false
    );
  }

  public SummaryManager(
    StoryHistory historyStore,
    ChatClient client,
    nl.llm.storyteller.core.config.AppConfig config,
    PromptLoader promptResourceLoader,
    PromptTemplateService promptTemplateService,
    SummaryPromptBuilder summaryPromptBuilder,
    TextMemory memory,
    DerivedMemoryTaskQueue taskQueue
  ) {
    this(historyStore, client, config, promptResourceLoader, promptTemplateService, summaryPromptBuilder,
      memory, taskQueue, false);
  }

  private SummaryManager(
    StoryHistory historyStore,
    ChatClient client,
    nl.llm.storyteller.core.config.AppConfig config,
    PromptLoader promptResourceLoader,
    PromptTemplateService promptTemplateService,
    SummaryPromptBuilder summaryPromptBuilder,
    TextMemory memory,
    DerivedMemoryTaskQueue taskQueue,
    boolean ownsTaskQueue
  ) {
    super(historyStore, client, config, promptResourceLoader, promptTemplateService, memory, taskQueue, ownsTaskQueue);
    this.summaryPromptBuilder = summaryPromptBuilder;
  }

  public String loadSummary() {
    return loadMemory();
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
  protected void markUpdated(int cutoffIndex) {
    historyStore.markSummarized(cutoffIndex);
  }

  @Override
  protected void ignoreFailure() {
    // Summary refresh is best-effort and must never interrupt the main chat flow.
  }
}
