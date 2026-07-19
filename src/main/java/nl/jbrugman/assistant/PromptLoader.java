package nl.jbrugman.assistant;

final class PromptLoader {
    private final AppConfig config;

    PromptLoader(AppConfig config) {
        this.config = config;
    }

    String loadSystemPrompt() {
        return FileSupport.readTextFile(config.systemPromptFile(), "You are a helpful assistant.");
    }

    String loadRulesPrompt() {
        return FileSupport.readTextFile(config.rulesFile(), "You are a helpful assistant.");
    }
}
