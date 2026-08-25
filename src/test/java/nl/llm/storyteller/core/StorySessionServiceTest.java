package nl.llm.storyteller.core;

import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.service.CanonicalStateManager;
import nl.llm.storyteller.core.service.CanonicalStatePromptBuilder;
import nl.llm.storyteller.core.service.ChatClient;
import nl.llm.storyteller.core.service.HistoryStore;
import nl.llm.storyteller.core.service.PromptAssemblyService;
import nl.llm.storyteller.core.service.PromptResourceLoader;
import nl.llm.storyteller.core.service.PromptTemplateService;
import nl.llm.storyteller.core.service.RecentSummaryManager;
import nl.llm.storyteller.core.service.RecentSummaryPromptBuilder;
import nl.llm.storyteller.core.service.ResponseGuard;
import nl.llm.storyteller.core.service.StoryChatPromptBuilder;
import nl.llm.storyteller.core.service.StorySessionService;
import nl.llm.storyteller.core.service.SummaryManager;
import nl.llm.storyteller.core.service.SummaryPromptBuilder;
import nl.llm.storyteller.core.service.TurnManager;
import nl.llm.storyteller.core.service.TurnStateStore;
import nl.llm.storyteller.core.service.ValidationPromptBuilder;
import nl.llm.storyteller.core.service.GameModeDefinitionParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorySessionServiceTest {
    @Test
    @DisplayName("""
        Given a completed story turn at the configured cache-buster interval,
        When the story session persists that turn,
        Then it should send one silent cache-buster reset request afterward
        """)
    void shouldRunSilentPeriodicCacheBusterAfterConfiguredTurnInterval() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-periodic-cache-buster");
        writeOverride(baseDirectory, "systemprompts/application.config", """
            validation.enabled=false
            cacheBuster.interval=1
            """);

        nl.llm.storyteller.core.config.AppConfig config = nl.llm.storyteller.core.config.AppConfigLoader.load(baseDirectory, null);
        HistoryStore historyStore = new HistoryStore(config.historyFile(), config.legacyHistoryFile());
        PromptResourceLoader promptResourceLoader = new PromptResourceLoader(config);
        PromptTemplateService promptTemplateService = new PromptTemplateService(promptResourceLoader);
        SummaryManager summaryManager = new SummaryManager(
            historyStore, new NoOpChatClient(), config, promptResourceLoader, promptTemplateService,
            new SummaryPromptBuilder(promptResourceLoader, promptTemplateService)
        );
        RecentSummaryManager recentSummaryManager = new RecentSummaryManager(
            historyStore, new NoOpChatClient(), config, promptResourceLoader, promptTemplateService,
            new RecentSummaryPromptBuilder(promptResourceLoader, promptTemplateService)
        );
        CanonicalStateManager canonicalStateManager = new CanonicalStateManager(
            historyStore, new NoOpChatClient(), config, promptResourceLoader, promptTemplateService,
            new CanonicalStatePromptBuilder(promptResourceLoader, promptTemplateService)
        );
        RecordingChatClient recordingChatClient = new RecordingChatClient("Story response");
        StorySessionService storySessionService = new StorySessionService(
            config,
            historyStore,
            recordingChatClient,
            new ResponseGuard(new NoOpChatClient(), config),
            summaryManager,
            recentSummaryManager,
            canonicalStateManager,
            new PromptAssemblyService(
                historyStore,
                summaryManager,
                recentSummaryManager,
                canonicalStateManager,
                new TurnManager(
                    config,
                    promptResourceLoader,
                    promptTemplateService,
                    new GameModeDefinitionParser(),
                    new TurnStateStore(config.turnStateFile())
                ),
                new StoryChatPromptBuilder(promptResourceLoader, promptTemplateService),
                new ValidationPromptBuilder(promptResourceLoader, promptTemplateService)
            ),
            promptResourceLoader
        );

        try {
            assertEquals("Story response", storySessionService.handleUserTurn("Continue the story."));
            assertEquals(2, recordingChatClient.requestCount());
            List<Message> cacheBusterRequest = recordingChatClient.requests().getLast();
            assertTrue(cacheBusterRequest.getFirst().content().startsWith("Opaque reset cache-buster token:"));
            assertEquals(config.resetStoryCommand(), cacheBusterRequest.getLast().content());
        } finally {
            summaryManager.shutdown();
            recentSummaryManager.shutdown();
            canonicalStateManager.shutdown();
        }
    }

    @Test
    @DisplayName("""
        Given a reset-only turn that is meant to wake the model up,
        When the story session handles that reset instruction,
        Then the request should include a transient cache buster but no history or derived-memory updates should be persisted
        """)
    void shouldTreatResetAsTransientControlTurn() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-reset-turn");
        writeOverride(baseDirectory, "systemprompts/application.config", "validation.enabled=false");

        nl.llm.storyteller.core.config.AppConfig config = nl.llm.storyteller.core.config.AppConfigLoader.load(baseDirectory, null);
        HistoryStore historyStore = new HistoryStore(config.historyFile(), config.legacyHistoryFile());
        historyStore.appendTurn("Earlier prompt", "Earlier reply");
        PromptResourceLoader promptResourceLoader = new PromptResourceLoader(config);
        PromptTemplateService promptTemplateService = new PromptTemplateService(promptResourceLoader);
        StoryChatPromptBuilder storyChatPromptBuilder = new StoryChatPromptBuilder(promptResourceLoader, promptTemplateService);
        ValidationPromptBuilder validationPromptBuilder = new ValidationPromptBuilder(promptResourceLoader, promptTemplateService);
        SummaryManager summaryManager = new SummaryManager(
            historyStore,
            new NoOpChatClient(),
            config,
            promptResourceLoader,
            promptTemplateService,
            new SummaryPromptBuilder(promptResourceLoader, promptTemplateService)
        );
        RecentSummaryManager recentSummaryManager = new RecentSummaryManager(
            historyStore,
            new NoOpChatClient(),
            config,
            promptResourceLoader,
            promptTemplateService,
            new RecentSummaryPromptBuilder(promptResourceLoader, promptTemplateService)
        );
        CanonicalStateManager canonicalStateManager = new CanonicalStateManager(
            historyStore,
            new NoOpChatClient(),
            config,
            promptResourceLoader,
            promptTemplateService,
            new CanonicalStatePromptBuilder(promptResourceLoader, promptTemplateService)
        );
        PromptAssemblyService promptAssemblyService = new PromptAssemblyService(
            historyStore,
            summaryManager,
            recentSummaryManager,
            canonicalStateManager,
            new TurnManager(
                config,
                promptResourceLoader,
                promptTemplateService,
                new GameModeDefinitionParser(),
                new TurnStateStore(config.turnStateFile())
            ),
            storyChatPromptBuilder,
            validationPromptBuilder
        );
        RecordingChatClient recordingChatClient = new RecordingChatClient("Reset acknowledged.");
        StorySessionService storySessionService = new StorySessionService(
            config,
            historyStore,
            recordingChatClient,
            new ResponseGuard(new NoOpChatClient(), config),
            summaryManager,
            recentSummaryManager,
            canonicalStateManager,
            promptAssemblyService,
            promptResourceLoader
        );

        try {
            String response = storySessionService.handleUserTurn(config.resetStoryCommand());

            assertEquals("Reset acknowledged.", response);
            assertEquals(
                List.of(
                    new Message("user", "Earlier prompt"),
                    new Message("assistant", "Earlier reply")
                ),
                historyStore.load().messages()
            );
            assertFalse(Files.exists(config.summaryFile()));
            assertFalse(Files.exists(config.recentSummaryFile()));
            assertFalse(Files.exists(config.canonicalStateFile()));

            List<Message> sentMessages = recordingChatClient.messages();
            assertEquals(2, sentMessages.size());
            assertEquals("system", sentMessages.getFirst().role());
            assertTrue(sentMessages.getFirst().content().contains("Opaque reset cache-buster token:"));
            assertTrue(sentMessages.getFirst().content().startsWith("Opaque reset cache-buster token:"));
            assertEquals("user", sentMessages.getLast().role());
            assertEquals(config.resetStoryCommand(), sentMessages.getLast().content());
            assertFalse(sentMessages.getFirst().content().contains("Earlier prompt"));
            assertFalse(sentMessages.getFirst().content().contains("Earlier reply"));
        } finally {
            summaryManager.shutdown();
            recentSummaryManager.shutdown();
            canonicalStateManager.shutdown();
        }
    }

    @Test
    @DisplayName("""
        Given an existing last turn that should be retried,
        When the story session undoes that turn and performs a reset,
        Then the last turn should be removed from history and the original user input should be restored for editing
        """)
    void shouldUndoLastTurnAndReturnItsUserInput() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-undo-turn");
        writeOverride(baseDirectory, "systemprompts/application.config", "validation.enabled=false");

        nl.llm.storyteller.core.config.AppConfig config = nl.llm.storyteller.core.config.AppConfigLoader.load(baseDirectory, null);
        HistoryStore historyStore = new HistoryStore(config.historyFile(), config.legacyHistoryFile());
        historyStore.appendTurn("Original prompt", "Bad answer");
        PromptResourceLoader promptResourceLoader = new PromptResourceLoader(config);
        PromptTemplateService promptTemplateService = new PromptTemplateService(promptResourceLoader);
        StoryChatPromptBuilder storyChatPromptBuilder = new StoryChatPromptBuilder(promptResourceLoader, promptTemplateService);
        ValidationPromptBuilder validationPromptBuilder = new ValidationPromptBuilder(promptResourceLoader, promptTemplateService);
        SummaryManager summaryManager = new SummaryManager(
            historyStore,
            new NoOpChatClient(),
            config,
            promptResourceLoader,
            promptTemplateService,
            new SummaryPromptBuilder(promptResourceLoader, promptTemplateService)
        );
        RecentSummaryManager recentSummaryManager = new RecentSummaryManager(
            historyStore,
            new NoOpChatClient(),
            config,
            promptResourceLoader,
            promptTemplateService,
            new RecentSummaryPromptBuilder(promptResourceLoader, promptTemplateService)
        );
        CanonicalStateManager canonicalStateManager = new CanonicalStateManager(
            historyStore,
            new NoOpChatClient(),
            config,
            promptResourceLoader,
            promptTemplateService,
            new CanonicalStatePromptBuilder(promptResourceLoader, promptTemplateService)
        );
        PromptAssemblyService promptAssemblyService = new PromptAssemblyService(
            historyStore,
            summaryManager,
            recentSummaryManager,
            canonicalStateManager,
            new TurnManager(
                config,
                promptResourceLoader,
                promptTemplateService,
                new GameModeDefinitionParser(),
                new TurnStateStore(config.turnStateFile())
            ),
            storyChatPromptBuilder,
            validationPromptBuilder
        );
        RecordingChatClient recordingChatClient = new RecordingChatClient("Reset acknowledged.");
        StorySessionService storySessionService = new StorySessionService(
            config,
            historyStore,
            recordingChatClient,
            new ResponseGuard(new NoOpChatClient(), config),
            summaryManager,
            recentSummaryManager,
            canonicalStateManager,
            promptAssemblyService,
            promptResourceLoader
        );

        try {
            StorySessionService.UndoResult result = storySessionService.undoLastTurnAndReset();

            assertTrue(result.hasRestoredUserInput());
            assertEquals("Original prompt", result.restoredUserInput());
            assertEquals("Reset acknowledged.", result.resetResponse());
            assertEquals(List.of(), historyStore.load().messages());
            assertEquals("user", recordingChatClient.messages().getLast().role());
            assertEquals(config.resetStoryCommand(), recordingChatClient.messages().getLast().content());
        } finally {
            summaryManager.shutdown();
            recentSummaryManager.shutdown();
            canonicalStateManager.shutdown();
        }
    }

    private void writeOverride(Path baseDirectory, String relativePath, String content) throws Exception {
        Path path = baseDirectory.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private record NoOpChatClient() implements ChatClient {
        @Override
        public String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds) {
            return "";
        }
    }

    private static final class RecordingChatClient implements ChatClient {
        private final String response;
        private List<Message> messages = List.of();
        private final List<List<Message>> requests = new ArrayList<>();

        private RecordingChatClient(String response) {
            this.response = response;
        }

        @Override
        public String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds) {
            this.messages = List.copyOf(messages);
            this.requests.add(this.messages);
            return response;
        }

        List<Message> messages() {
            return messages;
        }

        int requestCount() {
            return requests.size();
        }

        List<List<Message>> requests() {
            return List.copyOf(requests);
        }
    }
}
