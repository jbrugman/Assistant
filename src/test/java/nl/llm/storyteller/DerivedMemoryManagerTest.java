package nl.llm.storyteller;

import nl.llm.storyteller.model.HistoryState;
import nl.llm.storyteller.model.Message;
import nl.llm.storyteller.service.ChatClient;
import nl.llm.storyteller.service.HistoryStore;
import nl.llm.storyteller.service.PromptResourceLoader;
import nl.llm.storyteller.service.PromptTemplateService;
import nl.llm.storyteller.service.RecentSummaryManager;
import nl.llm.storyteller.service.RecentSummaryPromptBuilder;
import nl.llm.storyteller.service.SummaryManager;
import nl.llm.storyteller.service.SummaryPromptBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivedMemoryManagerTest {
    @Test
    @DisplayName("""
        Given older story messages below the summary batch threshold,
        When the summary refresh job is prepared,
        Then no background summary update should be scheduled
        """)
    void shouldSkipSummaryRefreshUntilEnoughOlderMessagesExist() throws Exception {
        TestContext context = createContext(
            """
                recentSummary.maxRecentTurns=2
                summary.batchMessages=3
                """
        );
        context.historyStore().save(
            new HistoryState(
                List.of(
                    new Message("user", "Turn one"),
                    new Message("assistant", "Reply one"),
                    new Message("user", "Turn two"),
                    new Message("assistant", "Reply two"),
                    new Message("user", "Turn three"),
                    new Message("assistant", "Reply three")
                ),
                0,
                0,
                0
            )
        );

        SummaryManager summaryManager = createSummaryManager(context, new NoOpChatClient());
        try {
            assertNull(prepareJob(summaryManager));
        } finally {
            summaryManager.shutdown();
        }
    }

    @Test
    @DisplayName("""
        Given enough older turns outside the trailing raw-turn window,
        When the recent-summary refresh job is prepared,
        Then only the middle recent window should be sent to the summarizer
        """)
    void shouldPrepareRecentSummaryFromMiddleWindow() throws Exception {
        TestContext context = createContext(
            """
                chat.maxRecentTurns=2
                recentSummary.maxRecentTurns=4
                recentSummary.batchMessages=4
                """
        );
        context.historyStore().save(
            new HistoryState(
                List.of(
                    new Message("user", "Turn one"),
                    new Message("assistant", "Reply one"),
                    new Message("user", "Turn two"),
                    new Message("assistant", "Reply two"),
                    new Message("user", "Turn three"),
                    new Message("assistant", "Reply three"),
                    new Message("user", "Turn four"),
                    new Message("assistant", "Reply four")
                ),
                0,
                0,
                0
            )
        );

        RecentSummaryManager recentSummaryManager = createRecentSummaryManager(context, new NoOpChatClient());
        try {
            Object job = prepareJob(recentSummaryManager);

            assertNotNull(job);
            assertEquals(
                List.of(
                    new Message("user", "Turn one"),
                    new Message("assistant", "Reply one"),
                    new Message("user", "Turn two"),
                    new Message("assistant", "Reply two")
                ),
                pendingMessages(job)
            );
        } finally {
            recentSummaryManager.shutdown();
        }
    }

    @Test
    @DisplayName("""
        Given a running summary refresh whose cursor becomes stale before write-back,
        When the background update completes,
        Then it should not overwrite the summary file or advance the summary cursor
        """)
    void shouldSkipWriteBackWhenSummaryCursorChangedDuringBackgroundRefresh() throws Exception {
        TestContext context = createContext(
            """
                recentSummary.maxRecentTurns=2
                summary.batchMessages=2
                """
        );
        context.historyStore().save(
            new HistoryState(
                List.of(
                    new Message("user", "Turn one"),
                    new Message("assistant", "Reply one"),
                    new Message("user", "Turn two"),
                    new Message("assistant", "Reply two"),
                    new Message("user", "Turn three"),
                    new Message("assistant", "Reply three")
                ),
                0,
                0,
                0
            )
        );
        FileSupport.writeTextFile(context.config().summaryFile(), "Original summary");

        BlockingChatClient client = new BlockingChatClient("Updated summary");
        SummaryManager summaryManager = createSummaryManager(context, client);
        try {
            summaryManager.startUpdateSummaryIfNeeded();
            assertTrue(client.started().await(5, TimeUnit.SECONDS));

            context.historyStore().markSummarized(1);
            client.allowFinish().countDown();
            assertTrue(client.finished().await(5, TimeUnit.SECONDS));

            awaitWorkerIdle(summaryManager);

            assertEquals("Original summary", FileSupport.readTextFile(context.config().summaryFile()));
            assertEquals(1, context.historyStore().load().summaryCursor());
        } finally {
            summaryManager.shutdown();
        }
    }

    private TestContext createContext(String overrides) throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-derived-memory");
        Path configFile = baseDirectory.resolve("systemprompts/application.config");
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, overrides);

        AppConfig config = AppConfigLoader.load(baseDirectory, null);
        HistoryStore historyStore = new HistoryStore(config.historyFile(), config.legacyHistoryFile());
        PromptResourceLoader promptResourceLoader = new PromptResourceLoader(config);
        PromptTemplateService promptTemplateService = new PromptTemplateService(promptResourceLoader);
        return new TestContext(config, historyStore, promptResourceLoader, promptTemplateService);
    }

    private SummaryManager createSummaryManager(TestContext context, ChatClient client) {
        return new SummaryManager(
            context.historyStore(),
            client,
            context.config(),
            context.promptResourceLoader(),
            context.promptTemplateService(),
            new SummaryPromptBuilder(context.promptResourceLoader(), context.promptTemplateService())
        );
    }

    private RecentSummaryManager createRecentSummaryManager(TestContext context, ChatClient client) {
        return new RecentSummaryManager(
            context.historyStore(),
            client,
            context.config(),
            context.promptResourceLoader(),
            context.promptTemplateService(),
            new RecentSummaryPromptBuilder(context.promptResourceLoader(), context.promptTemplateService())
        );
    }

    private void awaitWorkerIdle(Object manager) {
        try {
            var runningField = manager.getClass().getSuperclass().getDeclaredField("running");
            runningField.setAccessible(true);
            AtomicBoolean running = (AtomicBoolean) runningField.get(manager);

            long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (running.get()) {
                if (System.nanoTime() >= deadlineNanos) {
                    throw new AssertionError("Background worker did not become idle within the timeout.");
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private Object prepareJob(Object manager) {
        try {
            Method method = manager.getClass().getDeclaredMethod("prepareJob");
            method.setAccessible(true);
            return method.invoke(manager);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Message> pendingMessages(Object job) {
        try {
            Method method = job.getClass().getDeclaredMethod("pendingMessages");
            method.setAccessible(true);
            return (List<Message>) method.invoke(job);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private record TestContext(
        AppConfig config,
        HistoryStore historyStore,
        PromptResourceLoader promptResourceLoader,
        PromptTemplateService promptTemplateService
    ) {}

    private record NoOpChatClient() implements ChatClient {
        @Override
        public String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds) {
            return "";
        }
    }

    private static final class BlockingChatClient implements ChatClient {
        private final String response;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch allowFinish = new CountDownLatch(1);
        private final CountDownLatch finished = new CountDownLatch(1);

        private BlockingChatClient(String response) {
            this.response = response;
        }

        @Override
        public String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds) throws InterruptedException {
            started.countDown();
            allowFinish.await(5, TimeUnit.SECONDS);
            finished.countDown();
            return response;
        }

        CountDownLatch started() {
            return started;
        }

        CountDownLatch allowFinish() {
            return allowFinish;
        }

        CountDownLatch finished() {
            return finished;
        }
    }
}
