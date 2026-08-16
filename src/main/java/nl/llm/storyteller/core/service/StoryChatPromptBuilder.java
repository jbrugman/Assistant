package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.model.StoryChatPromptInput;

import java.util.ArrayList;
import java.util.List;

public final class StoryChatPromptBuilder {
  private static final String SYSTEM = "system";
  private static final String USER = "user";

  private final PromptResourceLoader promptResourceLoader;
  private final PromptTemplateService promptTemplateService;

  public StoryChatPromptBuilder(
    PromptResourceLoader promptResourceLoader,
    PromptTemplateService promptTemplateService
  ) {
    this.promptResourceLoader = promptResourceLoader;
    this.promptTemplateService = promptTemplateService;
  }

  public List<Message> build(StoryChatPromptInput input) {
    List<Message> messages = new ArrayList<>();
    messages.add(new Message(SYSTEM, buildSystemMessage(input)));

    messages.addAll(input.recentMessages());
    messages.add(new Message(USER, appendInlineInstruction(input.userInput(), input.extraSystemInstruction())));
    return messages;
  }

  private String buildSystemMessage(StoryChatPromptInput input) {
    List<String> sections = new ArrayList<>();
    addIfPresent(sections, promptResourceLoader.loadSystemPrompt());
    addIfPresent(sections, promptTemplateService.buildFixedProtagonistsContext());
    addIfPresent(sections, promptTemplateService.buildCanonicalStateContext(input.canonicalState()));
    addIfPresent(sections, promptTemplateService.buildSummaryContext(input.summary()));
    addIfPresent(sections, promptTemplateService.buildRecentSummaryContext(input.recentSummary()));
    return String.join("\n\n", sections);
  }

  private String appendInlineInstruction(String userInput, String extraInstruction) {
    if (extraInstruction == null || extraInstruction.isBlank()) {
      return userInput;
    }
    return userInput + " " + extraInstruction.trim();
  }

  private void addIfPresent(List<String> sections, String content) {
    if (!content.isBlank()) {
      sections.add(content);
    }
  }
}
