package nl.llm.storyteller.service;

import nl.llm.storyteller.model.SummaryPromptInput;
import nl.llm.storyteller.AppConfig;
import nl.llm.storyteller.model.HistoryState;
import nl.llm.storyteller.model.Message;

import java.util.ArrayList;
import java.util.List;

public final class SummaryManager extends DerivedMemoryManager {
    private final SummaryPromptBuilder summaryPromptBuilder;

    public SummaryManager(
        HistoryStore historyStore,
        ChatClient client,
        AppConfig config,
        PromptResourceLoader promptResourceLoader,
        PromptTemplateService promptTemplateService,
        SummaryPromptBuilder summaryPromptBuilder
    ) {
        super(historyStore, client, config, promptResourceLoader, promptTemplateService, "summary-worker");
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
