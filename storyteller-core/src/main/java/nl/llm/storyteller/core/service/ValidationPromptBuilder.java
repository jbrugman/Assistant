package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.ValidationPromptInput;

public final class ValidationPromptBuilder {
  private final PromptLoader promptResourceLoader;
  private final PromptTemplateService promptTemplateService;

  public ValidationPromptBuilder(
    PromptLoader promptResourceLoader,
    PromptTemplateService promptTemplateService
  ) {
    this.promptResourceLoader = promptResourceLoader;
    this.promptTemplateService = promptTemplateService;
  }

  public String buildSystemPrompt() {
    return promptResourceLoader.loadValidationSystemPrompt();
  }

  public String buildRequest(ValidationPromptInput input) {
    return buildRequest(
      input,
      promptResourceLoader.loadRulesPrompt(),
      promptResourceLoader.loadFixedProtagonists()
    );
  }

  public String buildRequest(ValidationPromptInput input, String rules, String fixedProtagonists) {
    String request = promptTemplateService.buildValidationRequest(
      rules,
      promptTemplateService.buildFixedProtagonistsContext(fixedProtagonists),
      input.userInput(),
      input.draftResponse()
    );
    if (input.knowledgeGraphFacts().isBlank()) {
      return request;
    }
    return input.knowledgeGraphFacts() + "\n\n" + request;
  }
}
