package nl.llm.storyteller.service;

public final class PromptTemplateService {
    private final PromptResourceLoader promptResourceLoader;

    public PromptTemplateService(PromptResourceLoader promptResourceLoader) {
        this.promptResourceLoader = promptResourceLoader;
    }

    public String buildFixedProtagonistsContext() {
        String raw = promptResourceLoader.loadFixedProtagonists();
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
}
