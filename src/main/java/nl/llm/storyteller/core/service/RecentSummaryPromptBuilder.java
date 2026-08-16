package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.model.RecentSummaryPromptInput;

import java.util.ArrayList;
import java.util.List;

public final class RecentSummaryPromptBuilder {
  private static final String SYSTEM = "system";
  private static final String USER = "user";
  private static final String EMPTY_RECENT_SUMMARY = "No recent summary yet.";

  private final PromptResourceLoader promptResourceLoader;
  private final PromptTemplateService promptTemplateService;

  public RecentSummaryPromptBuilder(
    PromptResourceLoader promptResourceLoader,
    PromptTemplateService promptTemplateService
  ) {
    this.promptResourceLoader = promptResourceLoader;
    this.promptTemplateService = promptTemplateService;
  }

  public List<Message> build(RecentSummaryPromptInput input) {
    List<Message> messages = new ArrayList<>();
    messages.add(new Message(SYSTEM, buildSystemMessage()));
    messages.add(
      new Message(
        USER,
        "Existing recent summary:\n"
          + defaultIfBlank(input.existingRecentSummary())
          + "\n\nRecent story messages to incorporate:\n"
          + input.formattedHistory()
      )
    );
    return messages;
  }

  private String buildSystemMessage() {
    List<String> sections = new ArrayList<>();
    addIfPresent(sections, promptResourceLoader.loadRecentSummarySystemPrompt());
    addIfPresent(sections, promptTemplateService.buildFixedProtagonistsContext());
    return String.join("\n\n", sections);
  }

  private void addIfPresent(List<String> sections, String content) {
    if (!content.isBlank()) {
      sections.add(content);
    }
  }

  private String defaultIfBlank(String value) {
    return value == null || value.isBlank() ? RecentSummaryPromptBuilder.EMPTY_RECENT_SUMMARY : value;
  }
}
