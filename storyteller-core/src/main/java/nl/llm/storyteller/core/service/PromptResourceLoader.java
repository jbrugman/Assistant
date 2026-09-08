package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.FileSupport;

public final class PromptResourceLoader {
  private final nl.llm.storyteller.core.config.AppConfig config;
  private final StoryPromptSource storyPromptSource;

  public PromptResourceLoader(nl.llm.storyteller.core.config.AppConfig config) {
    this(config, null);
  }

  public PromptResourceLoader(
    nl.llm.storyteller.core.config.AppConfig config,
    StoryPromptSource storyPromptSource
  ) {
    this.config = config;
    this.storyPromptSource = storyPromptSource;
  }

  public String loadSystemPrompt() {
    if (storyPromptSource != null) {
      return storyPromptSource.load().systemPrompt();
    }
    return FileSupport.readRequiredTextFileOrResource(config.systemPromptFile(), config.baseDir());
  }

  public String loadRulesPrompt() {
    if (storyPromptSource != null) {
      return storyPromptSource.load().rules();
    }
    return FileSupport.readRequiredTextFileOrResource(config.rulesFile(), config.baseDir());
  }

  public String loadSummarySystemPrompt() {
    return FileSupport.readRequiredTextFileOrResource(config.summarySystemPromptFile(), config.baseDir());
  }

  public String loadRecentSummarySystemPrompt() {
    return FileSupport.readRequiredTextFileOrResource(config.recentSummarySystemPromptFile(), config.baseDir());
  }

  public String loadCanonicalStateSystemPrompt() {
    return FileSupport.readRequiredTextFileOrResource(config.canonicalStateSystemPromptFile(), config.baseDir());
  }

  public String loadFixedProtagonists() {
    if (storyPromptSource != null) {
      return storyPromptSource.load().fixedProtagonists();
    }
    return FileSupport.readRequiredTextFileOrResource(config.fixedProtagonistsFile(), config.baseDir());
  }

  public String loadFixedProtagonistsContextTemplate() {
    return FileSupport.readRequiredTextFileOrResource(config.fixedProtagonistsContextFile(), config.baseDir());
  }

  public String loadCanonicalStateContextTemplate() {
    return FileSupport.readRequiredTextFileOrResource(config.canonicalStateContextFile(), config.baseDir());
  }

  public String loadSummaryContextTemplate() {
    return FileSupport.readRequiredTextFileOrResource(config.summaryContextFile(), config.baseDir());
  }

  public String loadRecentSummaryContextTemplate() {
    return FileSupport.readRequiredTextFileOrResource(config.recentSummaryContextFile(), config.baseDir());
  }

  public String loadValidationSystemPrompt() {
    return FileSupport.readRequiredTextFileOrResource(config.validationSystemPromptFile(), config.baseDir());
  }

  public String loadValidationRequestTemplate() {
    return FileSupport.readRequiredTextFileOrResource(config.validationRequestTemplateFile(), config.baseDir());
  }

  public String loadTurnViolationSingleTemplate() {
    return FileSupport.readRequiredTextFileOrResource(config.turnViolationSingleTemplateFile(), config.baseDir());
  }

  public String loadTurnViolationPartyTemplate() {
    return FileSupport.readRequiredTextFileOrResource(config.turnViolationPartyTemplateFile(), config.baseDir());
  }

}
