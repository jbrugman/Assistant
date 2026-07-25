package nl.llm.storyteller.service;

import nl.llm.storyteller.AppConfig;
import nl.llm.storyteller.model.HistoryState;
import nl.llm.storyteller.model.Message;

import java.util.ArrayList;
import java.util.List;

public final class RecentSummaryManager extends DerivedMemoryManager {
    public RecentSummaryManager(HistoryStore historyStore, ChatClient client, AppConfig config, PromptLoader promptLoader) {
        super(historyStore, client, config, promptLoader, "recent-summary-worker");
    }

    public String loadRecentSummary() {
        return loadMemory(config.recentSummaryFile());
    }

    public void startUpdateIfNeeded() {
        triggerUpdateIfNeeded();
    }

    @Override
    protected boolean isEnabled() {
        return config.recentSummaryMaxTurns() > config.maxRecentTurns();
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
        return buildRecentSummaryMessages(existingContent, pendingMessages);
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

    private List<Message> buildRecentSummaryMessages(String existingRecentSummary, List<Message> pendingMessages) {
        String currentRecentSummary = (existingRecentSummary == null || existingRecentSummary.isBlank())
            ? "No recent summary yet."
            : existingRecentSummary;

        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", promptLoader.loadRecentSummarySystemPrompt()));
        addFixedProtagonistsIfPresent(messages);

        messages.add(
            new Message(
                "user",
                "Existing recent summary:\n" + currentRecentSummary + "\n\n"
                    + "Recent story messages to incorporate:\n" + formatHistory(pendingMessages)
            )
        );
        return messages;
    }
}
