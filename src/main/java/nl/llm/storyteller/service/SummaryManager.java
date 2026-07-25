package nl.llm.storyteller.service;

import nl.llm.storyteller.AppConfig;
import nl.llm.storyteller.model.HistoryState;
import nl.llm.storyteller.model.Message;

import java.util.ArrayList;
import java.util.List;

public final class SummaryManager extends DerivedMemoryManager {
    public SummaryManager(HistoryStore historyStore, ChatClient client, AppConfig config, PromptLoader promptLoader) {
        super(historyStore, client, config, promptLoader, "summary-worker");
    }

    public String loadSummary() {
        return loadMemory(config.summaryFile());
    }

    public void startUpdateSummaryIfNeeded() {
        triggerUpdateIfNeeded();
    }

    @Override
    protected boolean isEnabled() {
        return true;
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
        return buildSummaryMessages(existingContent, pendingMessages);
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

    private List<Message> buildSummaryMessages(String existingSummary, List<Message> pendingMessages) {
        String currentSummary = (existingSummary == null || existingSummary.isBlank())
            ? "No summary yet."
            : existingSummary;

        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", promptLoader.loadSummarySystemPrompt()));
        addFixedProtagonistsIfPresent(messages);

        messages.add(
            new Message(
                "user",
                "Existing long-term summary:\n" + currentSummary + "\n\n"
                    + "Older story messages to incorporate:\n" + formatHistory(pendingMessages)
            )
        );
        return messages;
    }
}
