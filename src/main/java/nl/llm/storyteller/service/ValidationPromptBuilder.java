package nl.llm.storyteller.service;

import nl.llm.storyteller.model.ValidationPromptInput;

public final class ValidationPromptBuilder {
    private final PromptResourceLoader promptResourceLoader;
    private final PromptTemplateService promptTemplateService;

    public ValidationPromptBuilder(
        PromptResourceLoader promptResourceLoader,
        PromptTemplateService promptTemplateService
    ) {
        this.promptResourceLoader = promptResourceLoader;
        this.promptTemplateService = promptTemplateService;
    }

    public String buildSystemPrompt() {
        return promptResourceLoader.loadValidationSystemPrompt();
    }

    public String buildRequest(ValidationPromptInput input) {
        return promptTemplateService.buildValidationRequest(
            promptResourceLoader.loadRulesPrompt(),
            promptTemplateService.buildFixedProtagonistsContext(),
            input.userInput(),
            input.draftResponse()
        );
    }
}
