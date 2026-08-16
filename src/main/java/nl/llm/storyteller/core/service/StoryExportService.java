package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.FileSupport;
import nl.llm.storyteller.core.model.Message;

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
  private static final String ASSISTANT = "assistant";
  private static final String EXPORT = "# Story Export\n\n";

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
    ensureExportable(messages, mode);
    String markdown = switch (mode) {
      case ALL -> exportAll(messages);
      case INTRO -> exportIntro(messages);
      case CLEAN -> exportClean(messages);
    };

    Path output = baseDir.resolve("story-export-" + FILE_TIMESTAMP.format(LocalDateTime.now(clock)) + ".md");
    FileSupport.writeTextFile(output, markdown);
    return output;
  }

  private void ensureExportable(List<Message> messages, ExportMode mode) {
    if (messages.isEmpty()) {
      throw new IllegalStateException("There is no story history to export yet.");
    }

    if (mode == ExportMode.CLEAN && messages.stream().noneMatch(message -> ASSISTANT.equals(message.role()))) {
      throw new IllegalStateException("There is no assistant story output to export yet.");
    }
  }

  private String exportAll(List<Message> messages) {
    StringBuilder markdown = new StringBuilder(EXPORT);
    for (Message message : messages) {
      if ("user".equals(message.role())) {
        appendBlock(markdown, "## Prompt", message.content());
      } else if (ASSISTANT.equals(message.role())) {
        appendBlock(markdown, "## Story", message.content());
      }
    }
    return markdown.toString().trim();
  }

  private String exportIntro(List<Message> messages) {
    StringBuilder markdown = new StringBuilder(EXPORT);
    for (Message message : messages) {
      if ("user".equals(message.role())) {
        markdown.append('*').append(escapeInlineMarkdown(message.content())).append("*\n\n");
      } else if (ASSISTANT.equals(message.role())) {
        markdown.append(message.content().trim()).append("\n\n");
      }
    }
    return markdown.toString().trim();
  }

  private String exportClean(List<Message> messages) {
    StringBuilder markdown = new StringBuilder(EXPORT);
    for (Message message : messages) {
      if (ASSISTANT.equals(message.role())) {
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
