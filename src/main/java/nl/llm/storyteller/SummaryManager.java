package nl.llm.storyteller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

final class SummaryManager {
    private final HistoryStore historyStore;
    private final LMStudioClient client;
    private final AppConfig config;
    private final PromptLoader promptLoader;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object lock = new Object();

    SummaryManager(HistoryStore historyStore, LMStudioClient client, AppConfig config, PromptLoader promptLoader) {
        this.historyStore = historyStore;
        this.client = client;
        this.config = config;
        this.promptLoader = promptLoader;
        this.executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
    }

    String loadSummary() {
        return FileSupport.readTextFile(config.summaryFile(), "");
    }

    void startUpdateSummaryIfNeeded() {
        synchronized (lock) {
            if (running.get()) {
                return;
            }

            SummaryJob job = prepareJob();
            if (job == null) {
                return;
            }

            running.set(true);
            executor.submit(() -> runSummaryJob(job));
        }
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private SummaryJob prepareJob() {
        HistoryState state = historyStore.load();
        List<Message> recent = historyStore.recentMessages(config.recentSummaryMaxTurns());
        int cutoffIndex = state.messages().size() - recent.size();
        int cursor = Math.max(0, Math.min(state.summaryCursor(), state.messages().size()));

        if (cutoffIndex <= cursor) {
            return null;
        }

        List<Message> pendingMessages = new ArrayList<>(state.messages().subList(cursor, cutoffIndex));
        if (pendingMessages.size() < config.summaryBatchMessages()) {
            return null;
        }

        return new SummaryJob(cursor, cutoffIndex, loadSummary(), pendingMessages);
    }

    private void runSummaryJob(SummaryJob job) {
        try {
            List<Message> summaryPrompt = buildSummaryMessages(job.existingSummary(), job.pendingMessages());
            String newSummary = client.chat(
                summaryPrompt,
                config.summaryOptions(),
                config.summaryRequestTimeoutSeconds()
            );

            synchronized (lock) {
                HistoryState currentState = historyStore.load();
                int currentCursor = Math.max(0, Math.min(currentState.summaryCursor(), currentState.messages().size()));
                if (currentCursor != job.cursor()) {
                    return;
                }

                FileSupport.writeTextFile(config.summaryFile(), newSummary);
                historyStore.markSummarized(job.cutoffIndex());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException ex) {
            ignoreSummaryFailure();
        } finally {
            running.set(false);
        }
    }

    private void ignoreSummaryFailure() {
        // Summary refresh is best-effort and must never interrupt the main chat flow.
    }

    private List<Message> buildSummaryMessages(String existingSummary, List<Message> pendingMessages) {
        StringBuilder formattedHistory = new StringBuilder();
        for (Message message : pendingMessages) {
            if (!formattedHistory.isEmpty()) {
                formattedHistory.append('\n');
            }
            formattedHistory.append(message.role().toUpperCase()).append(": ").append(message.content());
        }

        String currentSummary = (existingSummary == null || existingSummary.isBlank())
            ? "No summary yet."
            : existingSummary;

        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", promptLoader.loadSummarySystemPrompt()));

        String fixedProtagonists = promptLoader.loadFixedProtagonistsContext();
        if (!fixedProtagonists.isBlank()) {
            messages.add(new Message("system", fixedProtagonists));
        }

        messages.add(
            new Message(
                "user",
                "Existing long-term summary:\n" + currentSummary + "\n\n"
                    + "Older story messages to incorporate:\n" + formattedHistory
            )
        );
        return messages;
    }

    private record SummaryJob(int cursor, int cutoffIndex, String existingSummary, List<Message> pendingMessages) {}

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "summary-worker");
            thread.setDaemon(true);
            return thread;
        }
    }
}
