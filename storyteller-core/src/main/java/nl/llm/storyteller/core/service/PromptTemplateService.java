package nl.llm.storyteller.core.service;

public final class PromptTemplateService {
  private final PromptLoader promptResourceLoader;

  public PromptTemplateService(PromptLoader promptResourceLoader) {
    this.promptResourceLoader = promptResourceLoader;
  }

  public String buildFixedProtagonistsContext() {
    return buildFixedProtagonistsContext(promptResourceLoader.loadFixedProtagonists());
  }

  public String buildFixedProtagonistsContext(String raw) {
    if (raw.isBlank()) {
      return "";
    }
    return promptResourceLoader.loadFixedProtagonistsContextTemplate().formatted(raw);
  }

  public String buildCanonicalStateContext(String canonicalState) {
    return promptResourceLoader.loadCanonicalStateContextTemplate().formatted(canonicalState);
  }

  public String buildSummaryContext(String summary) {
    return promptResourceLoader.loadSummaryContextTemplate().formatted(summary);
  }

  public String buildRecentSummaryContext(String recentSummary) {
    return promptResourceLoader.loadRecentSummaryContextTemplate().formatted(recentSummary);
  }

  public String buildValidationRequest(String rulesPrompt, String fixedProtagonistsContext, String userInstruction, String assistantResponse) {
    return promptResourceLoader.loadValidationRequestTemplate()
      .formatted(rulesPrompt, fixedProtagonistsContext, userInstruction, assistantResponse);
  }

  public String buildTurnViolationSingleInstruction(int lowPenaltyHp, int highPenaltyHp) {
    return promptResourceLoader.loadTurnViolationSingleTemplate()
      .formatted(lowPenaltyHp, highPenaltyHp);
  }

  public String buildTurnViolationPartyInstruction() {
    return promptResourceLoader.loadTurnViolationPartyTemplate();
  }
}
