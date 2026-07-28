package nl.llm.storyteller.service;

import nl.llm.storyteller.FileSupport;
import nl.llm.storyteller.model.Message;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public final class StoryExportService {
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final HistoryStore historyStore;
    private final Path baseDir;
    private final Clock clock;

    public StoryExportService(HistoryStore historyStore, Path baseDir) {
        this(historyStore, baseDir, Clock.systemDefaultZone());
    }

    StoryExportService(HistoryStore historyStore, Path baseDir, Clock clock) {
        this.historyStore = Objects.requireNonNull(historyStore);
        this.baseDir = Objects.requireNonNull(baseDir);
        this.clock = Objects.requireNonNull(clock);
    }

    public Path export(ExportMode mode) {
        List<Message> messages = historyStore.load().messages();
        String markdown = switch (mode) {
            case ALL -> exportAll(messages);
            case INTRO -> exportIntro(messages);
            case CLEAN -> exportClean(messages);
        };

        Path output = baseDir.resolve("story-export-" + FILE_TIMESTAMP.format(LocalDateTime.now(clock)) + ".md");
        FileSupport.writeTextFile(output, markdown);
        return output;
    }

    private String exportAll(List<Message> messages) {
        StringBuilder markdown = new StringBuilder("# Story Export\n\n");
        for (Message message : messages) {
            if ("user".equals(message.role())) {
                appendBlock(markdown, "## Prompt", message.content());
            } else if ("assistant".equals(message.role())) {
                appendBlock(markdown, "## Story", message.content());
            }
        }
        return markdown.toString().trim();
    }

    private String exportIntro(List<Message> messages) {
        StringBuilder markdown = new StringBuilder("# Story Export\n\n");
        for (Message message : messages) {
            if ("user".equals(message.role())) {
                markdown.append('*').append(escapeInlineMarkdown(message.content())).append("*\n\n");
            } else if ("assistant".equals(message.role())) {
                markdown.append(message.content().trim()).append("\n\n");
            }
        }
        return markdown.toString().trim();
    }

    private String exportClean(List<Message> messages) {
        StringBuilder markdown = new StringBuilder("# Story Export\n\n");
        for (Message message : messages) {
            if ("assistant".equals(message.role())) {
                markdown.append(message.content().trim()).append("\n\n");
            }
        }
        return markdown.toString().trim();
    }

    private void appendBlock(StringBuilder markdown, String heading, String content) {
        markdown.append(heading).append("\n\n").append(content.trim()).append("\n\n");
    }

    private String escapeInlineMarkdown(String text) {
        return text.trim().replace("*", "\\*").replace("_", "\\_");
    }

    public enum ExportMode {
        ALL,
        INTRO,
        CLEAN
    }
}
