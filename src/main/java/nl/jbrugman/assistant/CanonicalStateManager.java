package nl.jbrugman.assistant;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

final class CanonicalStateManager {
    private final HistoryStore historyStore;
    private final LMStudioClient client;
    private final AppConfig config;
    private final PromptLoader promptLoader;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object lock = new Object();

    CanonicalStateManager(HistoryStore historyStore, LMStudioClient client, AppConfig config, PromptLoader promptLoader) {
        this.historyStore = historyStore;
        this.client = client;
        this.config = config;
        this.promptLoader = promptLoader;
        this.executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
    }

    String loadCanonicalState() {
        if (!isEnabled()) {
            return "";
        }
        return FileSupport.readTextFile(config.canonicalStateFile(), "");
    }

    void startUpdateIfNeeded() {
        if (!isEnabled()) {
            return;
        }

        synchronized (lock) {
            if (running.get()) {
                return;
            }

            CanonicalStateJob job = prepareJob();
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
        return "story".equals(config.appMode());
    }

    private CanonicalStateJob prepareJob() {
        HistoryState state = historyStore.load();
        List<Message> recent = historyStore.recentMessages(config.maxRecentTurns());
        int cutoffIndex = state.messages().size() - recent.size();
        int cursor = Math.max(0, Math.min(state.canonicalStateCursor(), state.messages().size()));

        if (cutoffIndex <= cursor) {
            return null;
        }

        List<Message> pendingMessages = new ArrayList<>(state.messages().subList(cursor, cutoffIndex));
        if (pendingMessages.size() < config.canonicalStateBatchMessages()) {
            return null;
        }

        return new CanonicalStateJob(cursor, cutoffIndex, loadCanonicalState(), pendingMessages);
    }

    private void runJob(CanonicalStateJob job) {
        try {
            List<Message> statePrompt = buildCanonicalStateMessages(job.existingCanonicalState(), job.pendingMessages());
            String newCanonicalState = client.chat(
                statePrompt,
                config.summaryOptions(),
                config.summaryRequestTimeoutSeconds()
            );

            synchronized (lock) {
                HistoryState currentState = historyStore.load();
                int currentCursor = Math.max(
                    0,
                    Math.min(currentState.canonicalStateCursor(), currentState.messages().size())
                );
                if (currentCursor != job.cursor()) {
                    return;
                }

                FileSupport.writeTextFile(config.canonicalStateFile(), newCanonicalState);
                historyStore.markCanonicalStateUpdated(job.cutoffIndex());
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
        // Canonical state refresh is best-effort and must never interrupt the main chat flow.
    }

    private List<Message> buildCanonicalStateMessages(String existingCanonicalState, List<Message> pendingMessages) {
        StringBuilder formattedHistory = new StringBuilder();
        for (Message message : pendingMessages) {
            if (!formattedHistory.isEmpty()) {
                formattedHistory.append('\n');
            }
            formattedHistory.append(message.role().toUpperCase()).append(": ").append(message.content());
        }

        String currentState = (existingCanonicalState == null || existingCanonicalState.isBlank())
            ? "Nog geen canonieke toestand."
            : existingCanonicalState;

        return List.of(
            new Message("system", promptLoader.loadCanonicalStateSystemPrompt()),
            new Message(
                "user",
                "Bestaande canonieke toestand:\n" + currentState + "\n\n"
                    + "Nieuwe oudere berichten om te verwerken:\n" + formattedHistory
            )
        );
    }

    private record CanonicalStateJob(
        int cursor,
        int cutoffIndex,
        String existingCanonicalState,
        List<Message> pendingMessages
    ) {}

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "canonical-state-worker");
            thread.setDaemon(true);
            return thread;
        }
    }
}
