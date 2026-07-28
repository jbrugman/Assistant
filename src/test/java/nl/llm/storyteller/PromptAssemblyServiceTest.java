package nl.llm.storyteller;

import nl.llm.storyteller.model.HistoryState;
import nl.llm.storyteller.model.Message;
import nl.llm.storyteller.service.CanonicalStateManager;
import nl.llm.storyteller.service.CanonicalStatePromptBuilder;
import nl.llm.storyteller.service.ChatClient;
import nl.llm.storyteller.service.HistoryStore;
import nl.llm.storyteller.service.PromptAssemblyService;
import nl.llm.storyteller.service.PromptResourceLoader;
import nl.llm.storyteller.service.PromptTemplateService;
import nl.llm.storyteller.service.RecentSummaryManager;
import nl.llm.storyteller.service.RecentSummaryPromptBuilder;
import nl.llm.storyteller.service.StoryChatPromptBuilder;
import nl.llm.storyteller.service.SummaryManager;
import nl.llm.storyteller.service.SummaryPromptBuilder;
import nl.llm.storyteller.service.ValidationPromptBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        try {
            PromptAssemblyService promptAssemblyService = new PromptAssemblyService(
                historyStore,
                summaryManager,
                recentSummaryManager,
                canonicalStateManager,
                storyChatPromptBuilder,
                validationPromptBuilder
            );

            List<Message> messages = promptAssemblyService.buildChatMessages("Newest user input");

            assertEquals(
                List.of(
                    new Message("system", "SYSTEM PROMPT"),
                    new Message("system", "FIXED PROTAGONISTS:\nFIXED DATA"),
                    new Message("system", "CANONICAL CONTEXT:\nCANONICAL STATE"),
                    new Message("system", "SUMMARY CONTEXT:\nLONG SUMMARY"),
                    new Message("system", "RECENT CONTEXT:\nRECENT SUMMARY"),
                    new Message("user", "Recent user 1"),
                    new Message("assistant", "Recent assistant 1"),
                    new Message("user", "Recent user 2"),
                    new Message("assistant", "Recent assistant 2"),
                    new Message("user", "Newest user input")
                ),
                messages
            );
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
