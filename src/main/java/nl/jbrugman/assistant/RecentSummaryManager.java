package nl.jbrugman.assistant;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

final class RecentSummaryManager {
    private final HistoryStore historyStore;
    private final LMStudioClient client;
    private final AppConfig config;
    private final PromptLoader promptLoader;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object lock = new Object();

    RecentSummaryManager(HistoryStore historyStore, LMStudioClient client, AppConfig config, PromptLoader promptLoader) {
        this.historyStore = historyStore;
        this.client = client;
        this.config = config;
        this.promptLoader = promptLoader;
        this.executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
    }

    String loadRecentSummary() {
        if (!isEnabled()) {
            return "";
        }
        return FileSupport.readTextFile(config.recentSummaryFile(), "");
    }

    void startUpdateIfNeeded() {
        if (!isEnabled()) {
            return;
        }

        synchronized (lock) {
            if (running.get()) {
                return;
            }

            RecentSummaryJob job = prepareJob();
            if (job == null) {
                return;
            }

            running.set(true);
            executor.submit(() -> runJob(job));
        }
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private boolean isEnabled() {
        return config.recentSummaryMaxTurns() > config.maxRecentTurns();
    }

    private RecentSummaryJob prepareJob() {
        HistoryState state = historyStore.load();
        List<Message> recent = historyStore.recentMessages(config.maxRecentTurns());
        int cutoffIndex = state.messages().size() - recent.size();
        int cursor = Math.max(0, Math.min(state.recentSummaryCursor(), state.messages().size()));

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

        return new RecentSummaryJob(cursor, cutoffIndex, loadRecentSummary(), windowMessages);
    }

    private void runJob(RecentSummaryJob job) {
        try {
            List<Message> recentSummaryPrompt = buildRecentSummaryMessages(job.existingRecentSummary(), job.pendingMessages());
            String newRecentSummary = client.chat(
                recentSummaryPrompt,
                config.summaryOptions(),
                config.summaryRequestTimeoutSeconds()
            );

            synchronized (lock) {
                HistoryState currentState = historyStore.load();
                int currentCursor = Math.max(0, Math.min(currentState.recentSummaryCursor(), currentState.messages().size()));
                if (currentCursor != job.cursor()) {
                    return;
                }

                FileSupport.writeTextFile(config.recentSummaryFile(), newRecentSummary);
                historyStore.markRecentSummarized(job.cutoffIndex());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException ex) {
            ignoreFailure();
        } finally {
            running.set(false);
        }
    }

    private void ignoreFailure() {
        // Recent summary refresh is best-effort and must never interrupt the main chat flow.
    }

    private List<Message> buildRecentSummaryMessages(String existingRecentSummary, List<Message> pendingMessages) {
        StringBuilder formattedHistory = new StringBuilder();
        for (Message message : pendingMessages) {
            if (!formattedHistory.isEmpty()) {
                formattedHistory.append('\n');
            }
            formattedHistory.append(message.role().toUpperCase()).append(": ").append(message.content());
        }

        String currentRecentSummary = (existingRecentSummary == null || existingRecentSummary.isBlank())
            ? "Nog geen recente samenvatting."
            : existingRecentSummary;

        return List.of(
            new Message("system", promptLoader.loadRecentSummarySystemPrompt()),
            new Message(
                "user",
                "Bestaande recente samenvatting:\n" + currentRecentSummary + "\n\n"
                    + "Nieuwe recente berichten om te verwerken:\n" + formattedHistory
            )
        );
    }

    private record RecentSummaryJob(int cursor, int cutoffIndex, String existingRecentSummary, List<Message> pendingMessages) {}

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "recent-summary-worker");
            thread.setDaemon(true);
            return thread;
        }
    }
}
