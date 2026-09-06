package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.FileSupport;
import nl.llm.storyteller.core.JsonSupport;
import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.model.Message;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

  public Path exportSessionBundle(AppConfig config) {
    if (historyStore.load().messages().isEmpty()) {
      throw new IllegalStateException("There is no story history to export yet.");
    }
    Path output = baseDir.resolve("story-session-" + FILE_TIMESTAMP.format(LocalDateTime.now(clock)) + ".zip");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output), StandardCharsets.UTF_8)) {
      writeManifest(zip);
      writeRequiredFile(zip, config.historyFile());
      writeOptionalFile(zip, "summary.md", config.summaryFile());
      writeOptionalFile(zip, "recent-summary.md", config.recentSummaryFile());
      writeOptionalFile(zip, "canonical-state.yaml", config.canonicalStateFile());
      writeOptionalFile(zip, "turn-state.json", config.turnStateFile());
      writeOptionalFile(zip, "knowledge-graph.json", config.knowledgeGraphFile());
      return output;
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not export session bundle to " + output + ".", ex);
    }
  }

  private void writeManifest(ZipOutputStream zip) throws IOException {
    var manifest = JsonSupport.OBJECT_MAPPER.createObjectNode();
    manifest.put("format", "storyteller-session");
    manifest.put("version", 1);
    manifest.putNull("title");
    writeEntry(zip, "manifest.json",
      JsonSupport.OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
  }

  private void writeRequiredFile(ZipOutputStream zip, Path path) throws IOException {
    if (!Files.isRegularFile(path)) {
      throw new IllegalStateException("Missing required session file: " + path);
    }
    writeEntry(zip, "history.json", Files.readAllBytes(path));
  }

  private void writeOptionalFile(ZipOutputStream zip, String name, Path path) throws IOException {
    if (Files.isRegularFile(path)) {
      writeEntry(zip, name, Files.readAllBytes(path));
    }
  }

  private void writeEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content);
    zip.closeEntry();
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
