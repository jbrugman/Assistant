package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.CanonicalStatePromptInput;
import nl.llm.storyteller.core.model.Message;

import java.util.ArrayList;
import java.util.List;

public final class CanonicalStatePromptBuilder {
  private static final String SYSTEM = "system";
  private static final String USER = "user";
  private static final String EMPTY_CANONICAL_STATE = "No canonical state yet.";

  private final PromptResourceLoader promptResourceLoader;
  private final PromptTemplateService promptTemplateService;

  public CanonicalStatePromptBuilder(
    PromptResourceLoader promptResourceLoader,
    PromptTemplateService promptTemplateService
  ) {
    this.promptResourceLoader = promptResourceLoader;
    this.promptTemplateService = promptTemplateService;
  }

  public List<Message> build(CanonicalStatePromptInput input) {
    List<Message> messages = new ArrayList<>();
    messages.add(new Message(SYSTEM, buildSystemMessage()));
    messages.add(
      new Message(
        USER,
        "Existing canonical state:\n"
          + defaultIfBlank(input.existingCanonicalState())
          + "\n\nOlder story messages to incorporate:\n"
          + input.formattedHistory()
      )
    );
    return messages;
  }

  private String buildSystemMessage() {
    List<String> sections = new ArrayList<>();
    addIfPresent(sections, promptResourceLoader.loadCanonicalStateSystemPrompt());
    addIfPresent(sections, promptTemplateService.buildFixedProtagonistsContext());
    return String.join("\n\n", sections);
  }

  private void addIfPresent(List<String> sections, String content) {
    if (!content.isBlank()) {
      sections.add(content);
    }
  }

  private String defaultIfBlank(String value) {
    return value == null || value.isBlank() ? CanonicalStatePromptBuilder.EMPTY_CANONICAL_STATE : value;
  }
}
