package nl.llm.storyteller.service;

import nl.llm.storyteller.AppConfig;
import nl.llm.storyteller.model.HistoryState;
import nl.llm.storyteller.model.Message;

import java.util.ArrayList;
import java.util.List;

public final class CanonicalStateManager extends DerivedMemoryManager {
  public CanonicalStateManager(HistoryStore historyStore, ChatClient client, AppConfig config, PromptLoader promptLoader) {
    super(historyStore, client, config, promptLoader, "canonical-state-worker");
  }

  public String loadCanonicalState() {
    return loadMemory(config.canonicalStateFile());
  }

  public void startUpdateIfNeeded() {
    triggerUpdateIfNeeded();
  }

  @Override
  protected boolean isEnabled() {
    return true;
  }

  @Override
  protected DerivedMemoryJob prepareJob() {
    HistoryState state = historyStore.load();
    List<Message> recent = historyStore.recentMessages(config.maxRecentTurns());
    int cutoffIndex = state.messages().size() - recent.size();
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
    return buildCanonicalStateMessages(existingContent, pendingMessages);
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

  private List<Message> buildCanonicalStateMessages(String existingCanonicalState, List<Message> pendingMessages) {
    String currentState = (existingCanonicalState == null || existingCanonicalState.isBlank())
      ? "No canonical state yet."
      : existingCanonicalState;

    List<Message> messages = new ArrayList<>();
    messages.add(new Message("system", promptLoader.loadCanonicalStateSystemPrompt()));
    addFixedProtagonistsIfPresent(messages);

    messages.add(
      new Message(
        "user",
        "Existing canonical state:\n" + currentState + "\n\n"
          + "Older story messages to incorporate:\n" + formatHistory(pendingMessages)
      )
    );
    return messages;
  }
}
