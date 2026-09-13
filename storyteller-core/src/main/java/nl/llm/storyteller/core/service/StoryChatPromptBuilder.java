package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.model.StoryChatPromptInput;

import java.util.ArrayList;
import java.util.List;

public final class StoryChatPromptBuilder {
  private static final String SYSTEM = "system";
  private static final String USER = "user";

  private final PromptLoader promptResourceLoader;
  private final PromptTemplateService promptTemplateService;

  public StoryChatPromptBuilder(
    PromptLoader promptResourceLoader,
    PromptTemplateService promptTemplateService
  ) {
    this.promptResourceLoader = promptResourceLoader;
    this.promptTemplateService = promptTemplateService;
  }

  public List<Message> build(StoryChatPromptInput input) {
    return build(
      input,
      promptResourceLoader.loadSystemPrompt(),
      promptResourceLoader.loadFixedProtagonists()
    );
  }

  public List<Message> build(
    StoryChatPromptInput input,
    String systemPrompt,
    String fixedProtagonists
  ) {
    List<Message> messages = new ArrayList<>();
    messages.add(new Message(SYSTEM, buildSystemMessage(input, systemPrompt, fixedProtagonists)));

    messages.addAll(input.recentMessages());
    messages.add(new Message(USER, appendInlineInstruction(input.userInput(), input.extraSystemInstruction())));
    return messages;
  }

  private String buildSystemMessage(
    StoryChatPromptInput input,
    String systemPrompt,
    String fixedProtagonists
  ) {
    List<String> sections = new ArrayList<>();
    addIfPresent(sections, systemPrompt);
    addIfPresent(sections, promptTemplateService.buildFixedProtagonistsContext(fixedProtagonists));
    addIfPresent(sections, promptTemplateService.buildCanonicalStateContext(input.canonicalState()));
    addIfPresent(sections, promptTemplateService.buildSummaryContext(input.summary()));
    addIfPresent(sections, promptTemplateService.buildRecentSummaryContext(input.recentSummary()));
    addIfPresent(sections, input.knowledgeGraphFacts());
    addIfPresent(sections, input.relevantPastStory());
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
