package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.model.SummaryPromptInput;

import java.util.ArrayList;
import java.util.List;

public final class SummaryPromptBuilder {
  private static final String SYSTEM = "system";
  private static final String USER = "user";
  private static final String EMPTY_SUMMARY = "No summary yet.";

  private final PromptResourceLoader promptResourceLoader;
  private final PromptTemplateService promptTemplateService;

  public SummaryPromptBuilder(
    PromptResourceLoader promptResourceLoader,
    PromptTemplateService promptTemplateService
  ) {
    this.promptResourceLoader = promptResourceLoader;
    this.promptTemplateService = promptTemplateService;
  }

  public List<Message> build(SummaryPromptInput input) {
    List<Message> messages = new ArrayList<>();
    messages.add(new Message(SYSTEM, buildSystemMessage()));
    messages.add(
      new Message(
        USER,
        "Existing long-term summary:\n"
          + defaultIfBlank(input.existingSummary())
          + "\n\nOlder story messages to incorporate:\n"
          + input.formattedHistory()
      )
    );
    return messages;
  }

  private String buildSystemMessage() {
    List<String> sections = new ArrayList<>();
    addIfPresent(sections, promptResourceLoader.loadSummarySystemPrompt());
    addIfPresent(sections, promptTemplateService.buildFixedProtagonistsContext());
    return String.join("\n\n", sections);
  }

  private void addIfPresent(List<String> sections, String content) {
    if (!content.isBlank()) {
      sections.add(content);
    }
  }

  private String defaultIfBlank(String value) {
    return value == null || value.isBlank() ? SummaryPromptBuilder.EMPTY_SUMMARY : value;
  }
}
