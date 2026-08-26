package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.model.CanonicalStatePromptInput;
import nl.llm.storyteller.core.model.HistoryState;
import nl.llm.storyteller.core.model.Message;

import java.util.ArrayList;
import java.util.List;

public final class CanonicalStateManager extends DerivedMemoryManager {
  private final CanonicalStatePromptBuilder canonicalStatePromptBuilder;

  public CanonicalStateManager(
    HistoryStore historyStore,
    ChatClient client,
    nl.llm.storyteller.core.config.AppConfig config,
    PromptResourceLoader promptResourceLoader,
    PromptTemplateService promptTemplateService,
    CanonicalStatePromptBuilder canonicalStatePromptBuilder
  ) {
    this(
      historyStore, client, config, promptResourceLoader, promptTemplateService, canonicalStatePromptBuilder,
      new DerivedMemoryTaskQueue(), true
    );
  }

  public CanonicalStateManager(
    HistoryStore historyStore,
    ChatClient client,
    nl.llm.storyteller.core.config.AppConfig config,
    PromptResourceLoader promptResourceLoader,
    PromptTemplateService promptTemplateService,
    CanonicalStatePromptBuilder canonicalStatePromptBuilder,
    DerivedMemoryTaskQueue taskQueue
  ) {
    this(historyStore, client, config, promptResourceLoader, promptTemplateService, canonicalStatePromptBuilder, taskQueue, false);
  }

  private CanonicalStateManager(
    HistoryStore historyStore,
    ChatClient client,
    nl.llm.storyteller.core.config.AppConfig config,
    PromptResourceLoader promptResourceLoader,
    PromptTemplateService promptTemplateService,
    CanonicalStatePromptBuilder canonicalStatePromptBuilder,
    DerivedMemoryTaskQueue taskQueue,
    boolean ownsTaskQueue
  ) {
    super(historyStore, client, config, promptResourceLoader, promptTemplateService, taskQueue, ownsTaskQueue);
    this.canonicalStatePromptBuilder = canonicalStatePromptBuilder;
  }

  public String loadCanonicalState() {
    return loadMemory(config.canonicalStateFile());
  }

  public void startUpdateIfNeeded() {
    triggerUpdateIfNeeded();
  }

  @Override
  protected boolean isDisabled() {
    return false;
  }

  @Override
  protected DerivedMemoryJob prepareJob() {
    HistoryState state = historyStore.load();
    int cutoffIndex = state.messages().size();
    int cursor = safeCursor(state.canonicalStateCursor(), state.messages().size());

    if (cutoffIndex <= cursor) {
      return null;
    }

    List<Message> pendingMessages = new ArrayList<>(state.messages().subList(cursor, cutoffIndex));
    if (pendingMessages.size() < config.canonicalStateBatchMessages()) {
      return null;
    }

    return new DerivedMemoryJob(cursor, cutoffIndex, loadCanonicalState(), pendingMessages);
  }

  @Override
  protected List<Message> buildUpdateMessages(String existingContent, List<Message> pendingMessages) {
    return canonicalStatePromptBuilder.build(
      new CanonicalStatePromptInput(existingContent, formatHistory(pendingMessages))
    );
  }

  @Override
  protected int currentCursor(HistoryState state) {
    return state.canonicalStateCursor();
  }

  @Override
  protected java.nio.file.Path targetFile() {
    return config.canonicalStateFile();
  }

  @Override
  protected void markUpdated(int cutoffIndex) {
    historyStore.markCanonicalStateUpdated(cutoffIndex);
  }

  @Override
  protected void ignoreFailure() {
    // Canonical state refresh is best-effort and must never interrupt the main chat flow.
  }
}
