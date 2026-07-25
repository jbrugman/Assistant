package nl.llm.storyteller.service;

import nl.llm.storyteller.AppConfig;
import nl.llm.storyteller.FileSupport;
import nl.llm.storyteller.model.HistoryState;
import nl.llm.storyteller.model.Message;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class DerivedMemoryManager {
    protected final HistoryStore historyStore;
    protected final ChatClient client;
    protected final AppConfig config;
    protected final PromptLoader promptLoader;

    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object lock = new Object();

    DerivedMemoryManager(
        HistoryStore historyStore,
        ChatClient client,
        AppConfig config,
        PromptLoader promptLoader,
        String workerName
    ) {
        this.historyStore = historyStore;
        this.client = client;
        this.config = config;
        this.promptLoader = promptLoader;
        this.executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory(workerName));
    }

    public final void shutdown() {
        executor.shutdownNow();
    }

    protected final void triggerUpdateIfNeeded() {
        if (!isEnabled()) {
            return;
        }

        synchronized (lock) {
            if (running.get()) {
                return;
            }

            DerivedMemoryJob job = prepareJob();
            if (job == null) {
                return;
            }

            running.set(true);
            executor.submit(() -> runJob(job));
        }
    }

    protected final String loadMemory(Path path) {
        if (!isEnabled()) {
            return "";
        }
        return FileSupport.readTextFile(path);
    }

    protected final int safeCursor(int cursor, int size) {
        return Math.clamp(cursor, 0, size);
    }

    protected final String formatHistory(List<Message> pendingMessages) {
        StringBuilder formattedHistory = new StringBuilder();
        for (Message message : pendingMessages) {
            if (!formattedHistory.isEmpty()) {
                formattedHistory.append('\n');
            }
            formattedHistory.append(message.role().toUpperCase()).append(": ").append(message.content());
        }
        return formattedHistory.toString();
    }

    protected final void addFixedProtagonistsIfPresent(List<Message> messages) {
        String fixedProtagonists = promptLoader.loadFixedProtagonistsContext();
        if (!fixedProtagonists.isBlank()) {
            messages.add(new Message("system", fixedProtagonists));
        }
    }

    private void runJob(DerivedMemoryJob job) {
        try {
            List<Message> prompt = buildUpdateMessages(job.existingContent(), job.pendingMessages());
            String updatedContent = client.chat(
                prompt,
                config.summaryOptions(),
                config.summaryRequestTimeoutSeconds()
            );

            synchronized (lock) {
                HistoryState currentState = historyStore.load();
                if (safeCursor(currentCursor(currentState), currentState.messages().size()) != job.cursor()) {
                    return;
                }

                FileSupport.writeTextFile(targetFile(), updatedContent);
                markUpdated(job.cutoffIndex());
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException _) {
            ignoreFailure();
        } finally {
            running.set(false);
        }
    }

    protected abstract boolean isEnabled();

    protected abstract DerivedMemoryJob prepareJob();

    protected abstract List<Message> buildUpdateMessages(String existingContent, List<Message> pendingMessages);

    protected abstract int currentCursor(HistoryState state);

    protected abstract Path targetFile();

    protected abstract void markUpdated(int cutoffIndex);

    protected abstract void ignoreFailure();

    protected record DerivedMemoryJob(int cursor, int cutoffIndex, String existingContent, List<Message> pendingMessages) {}

    private record DaemonThreadFactory(String workerName) implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, workerName);
            thread.setDaemon(true);
            return thread;
        }
    }
}
