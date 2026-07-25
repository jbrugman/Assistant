package nl.llm.storyteller;

final class PromptLoader {
    private final AppConfig config;

    PromptLoader(AppConfig config) {
        this.config = config;
    }

    String loadSystemPrompt() {
        return FileSupport.readRequiredTextFileOrResource(config.systemPromptFile(), config.baseDir());
    }

    String loadRulesPrompt() {
        return FileSupport.readRequiredTextFileOrResource(config.rulesFile(), config.baseDir());
    }

    String loadSummarySystemPrompt() {
        return FileSupport.readRequiredTextFileOrResource(config.summarySystemPromptFile(), config.baseDir());
    }

    String loadRecentSummarySystemPrompt() {
        return FileSupport.readRequiredTextFileOrResource(config.recentSummarySystemPromptFile(), config.baseDir());
    }

    String loadCanonicalStateSystemPrompt() {
        return FileSupport.readRequiredTextFileOrResource(config.canonicalStateSystemPromptFile(), config.baseDir());
    }

    String loadFixedProtagonistsContext() {
        String raw = FileSupport.readRequiredTextFileOrResource(config.fixedProtagonistsFile(), config.baseDir());
        if (raw.isBlank()) {
            return "";
        }
        return FileSupport.readRequiredTextFileOrResource(config.fixedProtagonistsContextFile(), config.baseDir()).formatted(raw);
    }

    String loadCanonicalStateContext(String canonicalState) {
        return FileSupport.readRequiredTextFileOrResource(config.canonicalStateContextFile(), config.baseDir())
            .formatted(canonicalState);
    }

    String loadSummaryContext(String summary) {
        return FileSupport.readRequiredTextFileOrResource(config.summaryContextFile(), config.baseDir())
            .formatted(summary);
    }

    String loadRecentSummaryContext(String recentSummary) {
        return FileSupport.readRequiredTextFileOrResource(config.recentSummaryContextFile(), config.baseDir())
            .formatted(recentSummary);
    }

    String loadValidationSystemPrompt() {
        return FileSupport.readRequiredTextFileOrResource(config.validationSystemPromptFile(), config.baseDir());
    }

    String loadValidationRequest(String rulesPrompt, String fixedProtagonistsContext, String userInstruction, String assistantResponse) {
        return FileSupport.readRequiredTextFileOrResource(config.validationRequestTemplateFile(), config.baseDir())
            .formatted(rulesPrompt, fixedProtagonistsContext, userInstruction, assistantResponse);
    }
}
