package nl.llm.storyteller;

final class PromptLoader {
    private static final String FIXED_PROTAGONISTS_CONTEXT = """
        Fixed protagonists. Treat these as baseline character facts unless the story explicitly changes them.
        Keep their identity, role, and deep traits consistent while still allowing believable scene-level development.

        %s
        """;

    private final AppConfig config;

    PromptLoader(AppConfig config) {
        this.config = config;
    }

    String loadSystemPrompt() {
        return FileSupport.readRequiredTextFile(config.systemPromptFile());
    }

    String loadRulesPrompt() {
        return FileSupport.readRequiredTextFile(config.rulesFile());
    }

    String loadSummarySystemPrompt() {
        return FileSupport.readRequiredTextFile(config.summarySystemPromptFile());
    }

    String loadRecentSummarySystemPrompt() {
        return FileSupport.readRequiredTextFile(config.recentSummarySystemPromptFile());
    }

    String loadCanonicalStateSystemPrompt() {
        return FileSupport.readRequiredTextFile(config.canonicalStateSystemPromptFile());
    }

    String loadFixedProtagonistsContext() {
        String raw = FileSupport.readRequiredTextFile(config.fixedProtagonistsFile());
        if (raw.isBlank()) {
            return "";
        }
        return FIXED_PROTAGONISTS_CONTEXT.formatted(raw);
    }
}
