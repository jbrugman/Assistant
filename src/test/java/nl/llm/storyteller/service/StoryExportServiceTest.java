package nl.llm.storyteller.service;

import nl.llm.storyteller.model.HistoryState;
import nl.llm.storyteller.model.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryExportServiceTest {
    @Test
    @DisplayName("""
        Given alternating user prompts and assistant story turns,
        When the intro export is written,
        Then user prompts should be rendered in italic markdown between story passages
        """)
    void shouldExportIntroWithItalicizedPrompts() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-export-intro");
        HistoryStore historyStore = new HistoryStore(baseDirectory.resolve("memory/history.json"), baseDirectory.resolve("memory/history.md"));
        historyStore.save(history(
            new Message("user", "Write the next scene."),
            new Message("assistant", "The room was quiet."),
            new Message("user", "Keep it tense."),
            new Message("assistant", "He did not sit down.")
        ));

        StoryExportService exportService = new StoryExportService(
            historyStore,
            baseDirectory,
            fixedClock()
        );

        Path exportFile = exportService.export(StoryExportService.ExportMode.INTRO);
        String markdown = Files.readString(exportFile);

        assertTrue(exportFile.getFileName().toString().startsWith("story-export-"));
        assertTrue(exportFile.getFileName().toString().endsWith(".md"));
        assertTrue(markdown.contains("*Write the next scene.*"));
        assertTrue(markdown.contains("The room was quiet."));
        assertTrue(markdown.contains("*Keep it tense.*"));
        assertTrue(markdown.contains("He did not sit down."));
    }

    @Test
    @DisplayName("""
        Given alternating user prompts and assistant story turns,
        When the clean export is written,
        Then only assistant story output should be included
        """)
    void shouldExportOnlyAssistantOutputForCleanMode() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-export-clean");
        HistoryStore historyStore = new HistoryStore(baseDirectory.resolve("memory/history.json"), baseDirectory.resolve("memory/history.md"));
        historyStore.save(history(
            new Message("user", "Prompt one"),
            new Message("assistant", "Story one"),
            new Message("user", "Prompt two"),
            new Message("assistant", "Story two")
        ));

        StoryExportService exportService = new StoryExportService(
            historyStore,
            baseDirectory,
            fixedClock()
        );

        String markdown = Files.readString(exportService.export(StoryExportService.ExportMode.CLEAN));

        assertTrue(markdown.contains("Story one"));
        assertTrue(markdown.contains("Story two"));
        assertFalse(markdown.contains("Prompt one"));
        assertFalse(markdown.contains("Prompt two"));
    }

    @Test
    @DisplayName("""
        Given alternating user prompts and assistant story turns,
        When the full export is written,
        Then prompts and story output should be rendered chronologically with explicit headings
        """)
    void shouldExportAllChronologically() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-export-all");
        HistoryStore historyStore = new HistoryStore(baseDirectory.resolve("memory/history.json"), baseDirectory.resolve("memory/history.md"));
        historyStore.save(history(
            new Message("user", "Prompt one"),
            new Message("assistant", "Story one"),
            new Message("user", "Prompt two"),
            new Message("assistant", "Story two")
        ));

        StoryExportService exportService = new StoryExportService(
            historyStore,
            baseDirectory,
            fixedClock()
        );

        String markdown = Files.readString(exportService.export(StoryExportService.ExportMode.ALL));

        assertTrue(markdown.contains("## Prompt"));
        assertTrue(markdown.contains("## Story"));
        assertTrue(markdown.indexOf("Prompt one") < markdown.indexOf("Story one"));
        assertTrue(markdown.indexOf("Prompt two") < markdown.indexOf("Story two"));
    }

    @Test
    @DisplayName("""
        Given no history file exists yet,
        When a story export is requested,
        Then a clear error should explain that there is no story history to export
        """)
    void shouldFailClearlyWhenNoHistoryExistsYet() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-export-no-history");
        HistoryStore historyStore = new HistoryStore(baseDirectory.resolve("memory/history.json"), baseDirectory.resolve("memory/history.md"));
        StoryExportService exportService = new StoryExportService(historyStore, baseDirectory, fixedClock());

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> exportService.export(StoryExportService.ExportMode.INTRO)
        );

        assertEquals("There is no story history to export yet.", error.getMessage());
    }

    @Test
    @DisplayName("""
        Given an existing but empty history file,
        When a story export is requested,
        Then a clear error should explain that there is no story history to export
        """)
    void shouldFailClearlyWhenHistoryIsEmpty() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-export-empty-history");
        HistoryStore historyStore = new HistoryStore(baseDirectory.resolve("memory/history.json"), baseDirectory.resolve("memory/history.md"));
        historyStore.save(new HistoryState(List.of(), 0, 0, 0));
        StoryExportService exportService = new StoryExportService(historyStore, baseDirectory, fixedClock());

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> exportService.export(StoryExportService.ExportMode.ALL)
        );

        assertEquals("There is no story history to export yet.", error.getMessage());
    }

    private HistoryState history(Message... messages) {
        return new HistoryState(List.of(messages), 0, 0, 0);
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-07-28T10:15:30Z"), ZoneId.systemDefault());
    }
}
