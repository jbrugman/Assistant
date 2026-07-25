package nl.llm.storyteller;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class AppConfig {
    private static final String OPTION_TEMPERATURE = "temperature";
    private static final String OPTION_TOP_K = "top_k";
    private static final String OPTION_TOP_P = "top_p";
    private static final String OPTION_MIN_P = "min_p";
    private static final String OPTION_REPEAT_PENALTY = "repeat_penalty";

    private final Path baseDir;
    private final Models models;
    private final FilesConfig files;
    private final ConversationConfig conversation;
    private final TimeoutConfig timeouts;
    private final ResponseConfig response;
    private final OptionsConfig options;
    private final String lmStudioUrl;
    private final UiTextConfig uiText;

    private AppConfig(
        Path baseDir,
        String lmStudioUrl,
        Models models,
        FilesConfig files,
        ConversationConfig conversation,
        TimeoutConfig timeouts,
        ResponseConfig response,
        OptionsConfig options,
        UiTextConfig uiText
    ) {
        this.baseDir = baseDir;
        this.lmStudioUrl = lmStudioUrl;
        this.models = models;
        this.files = files;
        this.conversation = conversation;
        this.timeouts = timeouts;
        this.response = response;
        this.options = options;
        this.uiText = uiText;
    }

    static AppConfig load() {
        return AppConfigLoader.load();
    }

    private AppConfig validate() {
        if (maxRecentTurns() < 1) {
            throw new IllegalArgumentException("chat.maxRecentTurns must be at least 1.");
        }
        if (recentSummaryMaxTurns() < maxRecentTurns()) {
            throw new IllegalArgumentException("recentSummary.maxRecentTurns must be >= chat.maxRecentTurns.");
        }
        if (summaryBatchMessages() < 1 || recentSummaryBatchMessages() < 1 || canonicalStateBatchMessages() < 1) {
            throw new IllegalArgumentException("Batch sizes must all be at least 1.");
        }
        return this;
    }

    String lmStudioUrl() {
        return lmStudioUrl;
    }

    Path baseDir() {
        return baseDir;
    }

    String chatModel() {
        return models.chatModel();
    }

    String validatorModel() {
        return models.validatorModel();
    }

    Path systemPromptFile() {
        return files.systemPromptFile();
    }

    Path rulesFile() {
        return files.rulesFile();
    }

    Path fixedProtagonistsFile() {
        return files.fixedProtagonistsFile();
    }

    Path fixedProtagonistsContextFile() {
        return files.fixedProtagonistsContextFile();
    }

    Path summaryFile() {
        return files.summaryFile();
    }

    Path summaryContextFile() {
        return files.summaryContextFile();
    }

    Path recentSummarySystemPromptFile() {
        return files.recentSummarySystemPromptFile();
    }

    Path recentSummaryContextFile() {
        return files.recentSummaryContextFile();
    }

    Path recentSummaryFile() {
        return files.recentSummaryFile();
    }

    Path summarySystemPromptFile() {
        return files.summarySystemPromptFile();
    }

    Path canonicalStateSystemPromptFile() {
        return files.canonicalStateSystemPromptFile();
    }

    Path canonicalStateContextFile() {
        return files.canonicalStateContextFile();
    }

    Path validationSystemPromptFile() {
        return files.validationSystemPromptFile();
    }

    Path validationRequestTemplateFile() {
        return files.validationRequestTemplateFile();
    }

    Path canonicalStateFile() {
        return files.canonicalStateFile();
    }

    Path historyFile() {
        return files.historyFile();
    }

    Path legacyHistoryFile() {
        return files.legacyHistoryFile();
    }

    int maxRecentTurns() {
        return conversation.maxRecentTurns();
    }

    int summaryBatchMessages() {
        return conversation.summaryBatchMessages();
    }

    int recentSummaryMaxTurns() {
        return conversation.recentSummaryMaxTurns();
    }

    int recentSummaryBatchMessages() {
        return conversation.recentSummaryBatchMessages();
    }

    int canonicalStateBatchMessages() {
        return conversation.canonicalStateBatchMessages();
    }

    int requestTimeoutSeconds() {
        return timeouts.requestTimeoutSeconds();
    }

    int summaryRequestTimeoutSeconds() {
        return timeouts.summaryRequestTimeoutSeconds();
    }

    int validationRequestTimeoutSeconds() {
        return timeouts.validationRequestTimeoutSeconds();
    }

    boolean hideReasoningBlocks() {
        return response.hideReasoningBlocks();
    }

    boolean validationEnabled() {
        return response.validationEnabled();
    }

    String validationFailClosedMessage() {
        return response.validationFailClosedMessage();
    }

    Map<String, Object> chatOptions() {
        return options.chatOptions();
    }

    Map<String, Object> summaryOptions() {
        return options.summaryOptions();
    }

    Map<String, Object> validationOptions() {
        return options.validationOptions();
    }

    String continueStoryCommand() {
        return uiText.continueStoryCommand();
    }

    String resetStoryCommand() {
        return uiText.resetStoryCommand();
    }

    String bannerStartText() {
        return uiText.bannerStartText();
    }

    String shortcutContinueHint() {
        return uiText.shortcutContinueHint();
    }

    String shortcutResetHint() {
        return uiText.shortcutResetHint();
    }

    String lmStudioRequestErrorText() {
        return uiText.lmStudioRequestErrorText();
    }

    String processHistoryErrorText() {
        return uiText.processHistoryErrorText();
    }

    private static Map<String, Object> linkedMapOf(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            values.put((String) entries[i], entries[i + 1]);
        }
        return values;
    }

    static AppConfig from(AppConfigSource source) {
        return new AppConfig(
            source.baseDir(),
            source.requiredString("lmstudio.url"),
            new Models(
                source.requiredString("model.chat"),
                source.requiredString("model.validator")
            ),
            new FilesConfig(
                source.requiredPath("file.systemPrompt"),
                source.requiredPath("file.rules"),
                source.requiredPath("file.fixedProtagonists"),
                source.requiredPath("file.fixedProtagonistsContext"),
                source.requiredPath("file.summarySystemPrompt"),
                source.requiredPath("file.summaryContext"),
                source.requiredPath("file.summary"),
                source.requiredPath("file.recentSummarySystemPrompt"),
                source.requiredPath("file.recentSummaryContext"),
                source.requiredPath("file.recentSummary"),
                source.requiredPath("file.canonicalStateSystemPrompt"),
                source.requiredPath("file.canonicalStateContext"),
                source.requiredPath("file.validationSystemPrompt"),
                source.requiredPath("file.validationRequestTemplate"),
                source.requiredPath("file.canonicalState"),
                source.requiredPath("file.history"),
                source.requiredPath("file.legacyHistory")
            ),
            new ConversationConfig(
                source.requiredInt("chat.maxRecentTurns"),
                source.requiredInt("recentSummary.maxRecentTurns"),
                source.requiredInt("summary.batchMessages"),
                source.requiredInt("recentSummary.batchMessages"),
                source.requiredInt("canonicalState.batchMessages")
            ),
            new TimeoutConfig(
                source.requiredInt("timeout.chatSeconds"),
                source.requiredInt("timeout.summarySeconds"),
                source.requiredInt("timeout.validationSeconds")
            ),
            new ResponseConfig(
                source.requiredBoolean("validation.enabled"),
                source.requiredBoolean("response.hideReasoningBlocks"),
                source.requiredString("response.validationFailClosedMessage")
            ),
            new OptionsConfig(
                linkedMapOf(
                    OPTION_TEMPERATURE, source.requiredDouble("chat.temperature"),
                    OPTION_TOP_K, source.requiredInt("chat.topK"),
                    OPTION_TOP_P, source.requiredDouble("chat.topP"),
                    OPTION_MIN_P, source.requiredDouble("chat.minP"),
                    OPTION_REPEAT_PENALTY, source.requiredDouble("chat.repeatPenalty")
                ),
                linkedMapOf(
                    OPTION_TEMPERATURE, source.requiredDouble("summary.temperature"),
                    OPTION_TOP_P, source.requiredDouble("summary.topP")
                ),
                linkedMapOf(
                    OPTION_TEMPERATURE, source.requiredDouble("validation.temperature"),
                    OPTION_TOP_P, source.requiredDouble("validation.topP")
                )
            ),
            new UiTextConfig(
                source.requiredString("command.continueStory"),
                source.requiredString("command.resetStory"),
                source.requiredString("ui.bannerStart"),
                source.requiredString("ui.shortcutContinueHint"),
                source.requiredString("ui.shortcutResetHint"),
                source.requiredString("ui.errorLmStudioRequest"),
                source.requiredString("ui.errorProcessHistory")
            )
        ).validate();
    }

    private record Models(String chatModel, String validatorModel) {}

    private record FilesConfig(
        Path systemPromptFile,
        Path rulesFile,
        Path fixedProtagonistsFile,
        Path fixedProtagonistsContextFile,
        Path summarySystemPromptFile,
        Path summaryContextFile,
        Path summaryFile,
        Path recentSummarySystemPromptFile,
        Path recentSummaryContextFile,
        Path recentSummaryFile,
        Path canonicalStateSystemPromptFile,
        Path canonicalStateContextFile,
        Path validationSystemPromptFile,
        Path validationRequestTemplateFile,
        Path canonicalStateFile,
        Path historyFile,
        Path legacyHistoryFile
    ) {}

    private record ConversationConfig(
        int maxRecentTurns,
        int recentSummaryMaxTurns,
        int summaryBatchMessages,
        int recentSummaryBatchMessages,
        int canonicalStateBatchMessages
    ) {}

    private record TimeoutConfig(
        int requestTimeoutSeconds,
        int summaryRequestTimeoutSeconds,
        int validationRequestTimeoutSeconds
    ) {}

    private record ResponseConfig(
        boolean validationEnabled,
        boolean hideReasoningBlocks,
        String validationFailClosedMessage
    ) {}

    private record OptionsConfig(
        Map<String, Object> chatOptions,
        Map<String, Object> summaryOptions,
        Map<String, Object> validationOptions
    ) {
        private OptionsConfig {
            chatOptions = Map.copyOf(chatOptions);
            summaryOptions = Map.copyOf(summaryOptions);
            validationOptions = Map.copyOf(validationOptions);
        }
    }

    private record UiTextConfig(
        String continueStoryCommand,
        String resetStoryCommand,
        String bannerStartText,
        String shortcutContinueHint,
        String shortcutResetHint,
        String lmStudioRequestErrorText,
        String processHistoryErrorText
    ) {}
}
