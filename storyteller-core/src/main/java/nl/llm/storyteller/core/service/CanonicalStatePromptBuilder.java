package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.CanonicalStatePromptInput;
import nl.llm.storyteller.core.model.Message;

import java.util.ArrayList;
import java.util.List;

public final class CanonicalStatePromptBuilder {
  private static final String SYSTEM = "system";
  private static final String USER = "user";
  private static final String EMPTY_CANONICAL_STATE = "No canonical state yet.";

  private final PromptLoader promptResourceLoader;
  private final PromptTemplateService promptTemplateService;

  public CanonicalStatePromptBuilder(
    PromptLoader promptResourceLoader,
    PromptTemplateService promptTemplateService
  ) {
    this.promptResourceLoader = promptResourceLoader;
    this.promptTemplateService = promptTemplateService;
  }

  public List<Message> build(CanonicalStatePromptInput input) {
    return build(input, promptResourceLoader.loadFixedProtagonists());
  }

  public List<Message> build(CanonicalStatePromptInput input, String fixedProtagonists) {
    List<Message> messages = new ArrayList<>();
    messages.add(new Message(SYSTEM, buildSystemMessage(fixedProtagonists)));
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

  private String buildSystemMessage(String fixedProtagonists) {
    List<String> sections = new ArrayList<>();
    addIfPresent(sections, promptResourceLoader.loadCanonicalStateSystemPrompt());
    addIfPresent(sections, promptTemplateService.buildFixedProtagonistsContext(fixedProtagonists));
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
