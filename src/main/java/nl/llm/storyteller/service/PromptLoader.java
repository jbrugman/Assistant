package nl.llm.storyteller.service;

import nl.llm.storyteller.AppConfig;
import nl.llm.storyteller.FileSupport;

public final class PromptLoader {
    private final AppConfig config;

    public PromptLoader(AppConfig config) {
        this.config = config;
    }

    public String loadSystemPrompt() {
        return FileSupport.readRequiredTextFileOrResource(config.systemPromptFile(), config.baseDir());
    }

    public String loadRulesPrompt() {
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

    public String loadFixedProtagonistsContext() {
        String raw = FileSupport.readRequiredTextFileOrResource(config.fixedProtagonistsFile(), config.baseDir());
        if (raw.isBlank()) {
            return "";
        }
        return FileSupport.readRequiredTextFileOrResource(config.fixedProtagonistsContextFile(), config.baseDir()).formatted(raw);
    }

    public String loadCanonicalStateContext(String canonicalState) {
        return FileSupport.readRequiredTextFileOrResource(config.canonicalStateContextFile(), config.baseDir())
            .formatted(canonicalState);
    }

    public String loadSummaryContext(String summary) {
        return FileSupport.readRequiredTextFileOrResource(config.summaryContextFile(), config.baseDir())
            .formatted(summary);
    }

    public String loadRecentSummaryContext(String recentSummary) {
        return FileSupport.readRequiredTextFileOrResource(config.recentSummaryContextFile(), config.baseDir())
            .formatted(recentSummary);
    }

    public String loadValidationSystemPrompt() {
        return FileSupport.readRequiredTextFileOrResource(config.validationSystemPromptFile(), config.baseDir());
    }

    public String loadValidationRequest(String rulesPrompt, String fixedProtagonistsContext, String userInstruction, String assistantResponse) {
        return FileSupport.readRequiredTextFileOrResource(config.validationRequestTemplateFile(), config.baseDir())
            .formatted(rulesPrompt, fixedProtagonistsContext, userInstruction, assistantResponse);
    }
}
