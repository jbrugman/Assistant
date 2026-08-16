package nl.llm.storyteller.core;

import nl.llm.storyteller.core.model.HistoryState;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.service.CanonicalStateManager;
import nl.llm.storyteller.core.service.CanonicalStatePromptBuilder;
import nl.llm.storyteller.core.service.ChatClient;
import nl.llm.storyteller.core.service.HistoryStore;
import nl.llm.storyteller.core.service.GameModeDefinitionParser;
import nl.llm.storyteller.core.service.PromptAssemblyService;
import nl.llm.storyteller.core.service.PromptResourceLoader;
import nl.llm.storyteller.core.service.PromptTemplateService;
import nl.llm.storyteller.core.service.RecentSummaryManager;
import nl.llm.storyteller.core.service.RecentSummaryPromptBuilder;
import nl.llm.storyteller.core.service.StoryChatPromptBuilder;
import nl.llm.storyteller.core.service.SummaryManager;
import nl.llm.storyteller.core.service.SummaryPromptBuilder;
import nl.llm.storyteller.core.service.TurnManager;
import nl.llm.storyteller.core.service.TurnStateStore;
import nl.llm.storyteller.core.service.ValidationPromptBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptAssemblyServiceTest {
    @Test
    @DisplayName("""
        Given fixed protagonists, canonical state, summaries, and two recent turns,
        When the storyteller prompt stack is assembled,
        Then system context should come first, followed by the recent raw turns and the latest user message
        """)
    void shouldAssemblePromptInExpectedOrder() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-prompt-assembly");
        writeOverride(baseDirectory, "systemprompts/systemprompt.md", "SYSTEM PROMPT");
        writeOverride(baseDirectory, "systemprompts/fixed_protagonists.yml", "FIXED DATA");
        writeOverride(baseDirectory, "systemprompts/fixedprotagonistscontext.md", "FIXED PROTAGONISTS:%n%s");
        writeOverride(baseDirectory, "systemprompts/summarycontext.md", "SUMMARY CONTEXT:%n%s");
        writeOverride(baseDirectory, "systemprompts/recentsummarycontext.md", "RECENT CONTEXT:%n%s");
        writeOverride(baseDirectory, "systemprompts/canonicalstatecontext.md", "CANONICAL CONTEXT:%n%s");
        Files.createDirectories(baseDirectory.resolve("memory"));

        AppConfig config = AppConfigLoader.load(baseDirectory, null);
        HistoryStore historyStore = getHistoryStore(config);

        FileSupport.writeTextFile(config.summaryFile(), "LONG SUMMARY");
        FileSupport.writeTextFile(config.recentSummaryFile(), "RECENT SUMMARY");
        FileSupport.writeTextFile(config.canonicalStateFile(), "CANONICAL STATE");

        PromptResourceLoader promptResourceLoader = new PromptResourceLoader(config);
        PromptTemplateService promptTemplateService = new PromptTemplateService(promptResourceLoader);
        StoryChatPromptBuilder storyChatPromptBuilder = new StoryChatPromptBuilder(
            promptResourceLoader,
            promptTemplateService
        );
        ValidationPromptBuilder validationPromptBuilder = new ValidationPromptBuilder(
            promptResourceLoader,
            promptTemplateService
        );
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
        TurnManager turnManager = new TurnManager(
            config,
            promptResourceLoader,
            promptTemplateService,
            new GameModeDefinitionParser(),
            new TurnStateStore(config.turnStateFile())
        );

        try {
            PromptAssemblyService promptAssemblyService = new PromptAssemblyService(
                historyStore,
                summaryManager,
                recentSummaryManager,
                canonicalStateManager,
                turnManager,
                storyChatPromptBuilder,
                validationPromptBuilder
            );

            List<Message> messages = promptAssemblyService.buildChatMessages("Newest user input");

            assertEquals(
                List.of(
                    new Message("system", """
                        SYSTEM PROMPT

                        FIXED PROTAGONISTS:
                        FIXED DATA

                        CANONICAL CONTEXT:
                        CANONICAL STATE

                        SUMMARY CONTEXT:
                        LONG SUMMARY

                        RECENT CONTEXT:
                        RECENT SUMMARY
                        """.stripIndent().trim()),
                    new Message("user", "Recent user 1"),
                    new Message("assistant", "Recent assistant 1"),
                    new Message("user", "Recent user 2"),
                    new Message("assistant", "Recent assistant 2"),
                    new Message("user", "Newest user input")
                ),
                messages
            );
            assertEquals(
                1L,
                messages.stream().filter(message -> "system".equals(message.role())).count()
            );
        } finally {
            summaryManager.shutdown();
            recentSummaryManager.shutdown();
            canonicalStateManager.shutdown();
        }
    }

    @Test
    @DisplayName("""
        Given turn-based mode is enabled for a fixed party,
        When the same protagonist tries to act twice before the round is complete,
        Then the assembled prompt should append the turn violation rule to the latest user turn
        """)
    void shouldInjectTurnViolationInstructionForExtraMove() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-turn-based-prompt");
        writeOverride(baseDirectory, "systemprompts/systemprompt.md", "SYSTEM PROMPT");
        writeOverride(baseDirectory, "systemprompts/fixedprotagonistscontext.md", "FIXED PROTAGONISTS:%n%s");
        writeOverride(baseDirectory, "systemprompts/summarycontext.md", "SUMMARY CONTEXT:%n%s");
        writeOverride(baseDirectory, "systemprompts/recentsummarycontext.md", "RECENT CONTEXT:%n%s");
        writeOverride(baseDirectory, "systemprompts/canonicalstatecontext.md", "CANONICAL CONTEXT:%n%s");
        writeOverride(baseDirectory, "systemprompts/fixed_protagonists.yml", """
            game:
              trigger_word: "start"

            fixed_protagonist:
              - name: "Eldrin"
              - name: "Thorin"
            """);
        writeOverride(baseDirectory, "systemprompts/application.config", """
            game.turnBasedModeEnabled=true
            game.turnPenaltySingleLowHp=5
            game.turnPenaltySingleHighHp=10
            """);
        Files.createDirectories(baseDirectory.resolve("memory"));

        AppConfig config = AppConfigLoader.load(baseDirectory, null);
        HistoryStore historyStore = new HistoryStore(config.historyFile(), config.legacyHistoryFile());
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
        TurnManager turnManager = new TurnManager(
            config,
            promptResourceLoader,
            promptTemplateService,
            new GameModeDefinitionParser(),
            new TurnStateStore(config.turnStateFile())
        );

        try {
            PromptAssemblyService promptAssemblyService = new PromptAssemblyService(
                historyStore,
                summaryManager,
                recentSummaryManager,
                canonicalStateManager,
                turnManager,
                storyChatPromptBuilder,
                validationPromptBuilder
            );

            promptAssemblyService.buildChatMessages("start");
            promptAssemblyService.buildChatMessages("(Eldrin) I open the stone door.");
            List<Message> messages = promptAssemblyService.buildChatMessages("(Eldrin) I cast another spell immediately.");

            assertEquals(
                1L,
                messages.stream().filter(message -> "system".equals(message.role())).count()
            );
            Message latestUserInput = messages.getLast();

            assertEquals("user", latestUserInput.role());
            assertTrue(latestUserInput.content().startsWith("(Eldrin) I cast another spell immediately."));
            assertTrue(latestUserInput.content().contains("(this action is attempted out of turn."));
            assertTrue(latestUserInput.content().contains("must lose 5 or 10 health points"));
        } finally {
            summaryManager.shutdown();
            recentSummaryManager.shutdown();
            canonicalStateManager.shutdown();
        }
    }

    @Test
    @DisplayName("""
        Given recent history exists,
        When a reset control prompt is assembled,
        Then the reset request should keep the combined system context but omit raw recent turns
        """)
    void shouldAssembleResetPromptWithoutRecentHistory() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-reset-prompt-assembly");
        writeOverride(baseDirectory, "systemprompts/systemprompt.md", "SYSTEM PROMPT");
        writeOverride(baseDirectory, "systemprompts/fixed_protagonists.yml", "FIXED DATA");
        writeOverride(baseDirectory, "systemprompts/fixedprotagonistscontext.md", "FIXED PROTAGONISTS:%n%s");
        writeOverride(baseDirectory, "systemprompts/summarycontext.md", "SUMMARY CONTEXT:%n%s");
        writeOverride(baseDirectory, "systemprompts/recentsummarycontext.md", "RECENT CONTEXT:%n%s");
        writeOverride(baseDirectory, "systemprompts/canonicalstatecontext.md", "CANONICAL CONTEXT:%n%s");
        Files.createDirectories(baseDirectory.resolve("memory"));

        AppConfig config = AppConfigLoader.load(baseDirectory, null);
        HistoryStore historyStore = getHistoryStore(config);
        FileSupport.writeTextFile(config.summaryFile(), "LONG SUMMARY");
        FileSupport.writeTextFile(config.recentSummaryFile(), "RECENT SUMMARY");
        FileSupport.writeTextFile(config.canonicalStateFile(), "CANONICAL STATE");

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
        TurnManager turnManager = new TurnManager(
            config,
            promptResourceLoader,
            promptTemplateService,
            new GameModeDefinitionParser(),
            new TurnStateStore(config.turnStateFile())
        );

        try {
            PromptAssemblyService promptAssemblyService = new PromptAssemblyService(
                historyStore,
                summaryManager,
                recentSummaryManager,
                canonicalStateManager,
                turnManager,
                storyChatPromptBuilder,
                validationPromptBuilder
            );

            List<Message> messages = promptAssemblyService.buildResetMessages(config.resetStoryCommand());

            assertEquals(2, messages.size());
            assertEquals("system", messages.getFirst().role());
            assertEquals("user", messages.getLast().role());
            assertEquals(config.resetStoryCommand(), messages.getLast().content());
        } finally {
            summaryManager.shutdown();
            recentSummaryManager.shutdown();
            canonicalStateManager.shutdown();
        }
    }

    private static HistoryStore getHistoryStore(AppConfig config) {
        HistoryStore historyStore = new HistoryStore(config.historyFile(), config.legacyHistoryFile());
        historyStore.save(
            new HistoryState(
                List.of(
                    new Message("user", "Older user"),
                    new Message("assistant", "Older assistant"),
                    new Message("user", "Recent user 1"),
                    new Message("assistant", "Recent assistant 1"),
                    new Message("user", "Recent user 2"),
                    new Message("assistant", "Recent assistant 2")
                ),
                0,
                0,
                0
            )
        );
        return historyStore;
    }

    private void writeOverride(Path baseDirectory, String relativePath, String content) {
        Path path = baseDirectory.resolve(relativePath);
        try {
            Files.createDirectories(path.getParent());
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
        FileSupport.writeTextFile(path, content);
    }

    private static final class NoOpChatClient implements ChatClient {
        @Override
        public String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds) {
            return "";
        }
    }
}
