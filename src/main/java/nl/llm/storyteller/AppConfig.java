package nl.llm.storyteller;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AppConfig {
    private static final String OPTION_TEMPERATURE = "temperature";
    private static final String OPTION_TOP_K = "top_k";
    private static final String OPTION_TOP_P = "top_p";
    private static final String OPTION_MIN_P = "min_p";
    private static final String OPTION_REPEAT_PENALTY = "repeat_penalty";

    private final Path baseDir;
    private final ModelAccessConfig modelAccess;
    private final FilesConfig files;
    private final ConversationConfig conversation;
    private final ExecutionConfig execution;
    private final RuntimeTextConfig runtimeText;
    private final OptionsConfig options;

    private AppConfig(
        Path baseDir,
        ModelAccessConfig modelAccess,
        FilesConfig files,
        ConversationConfig conversation,
        ExecutionConfig execution,
        RuntimeTextConfig runtimeText,
        OptionsConfig options
    ) {
        this.baseDir = baseDir;
        this.modelAccess = modelAccess;
        this.files = files;
        this.conversation = conversation;
        this.execution = execution;
        this.runtimeText = runtimeText;
        this.options = options;
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
        if (chatFailureThreshold() < 1 || validationFailureThreshold() < 1 || backgroundFailureThreshold() < 1) {
            throw new IllegalArgumentException("Resilience failure thresholds must all be at least 1.");
        }
        if (chatCooldownSeconds() < 1 || validationCooldownSeconds() < 1 || backgroundCooldownSeconds() < 1) {
            throw new IllegalArgumentException("Resilience cooldown values must all be at least 1 second.");
        }
        return this;
    }

    public String lmStudioUrl() {
        return modelAccess.lmStudioUrl();
    }

    public Path baseDir() {
        return baseDir;
    }

    public String chatModel() {
        return modelAccess.chatModel();
    }

    public String validatorModel() {
        return modelAccess.validatorModel();
    }

    public Path systemPromptFile() {
        return files.systemPromptFile();
    }

    public Path rulesFile() {
        return files.rulesFile();
    }

    public Path fixedProtagonistsFile() {
        return files.fixedProtagonistsFile();
    }

    public Path fixedProtagonistsContextFile() {
        return files.fixedProtagonistsContextFile();
    }

    public Path summaryFile() {
        return files.summaryFile();
    }

    public Path summaryContextFile() {
        return files.summaryContextFile();
    }

    public Path recentSummarySystemPromptFile() {
        return files.recentSummarySystemPromptFile();
    }

    public Path recentSummaryContextFile() {
        return files.recentSummaryContextFile();
    }

    public Path recentSummaryFile() {
        return files.recentSummaryFile();
    }

    public Path summarySystemPromptFile() {
        return files.summarySystemPromptFile();
    }

    public Path canonicalStateSystemPromptFile() {
        return files.canonicalStateSystemPromptFile();
    }

    public Path canonicalStateContextFile() {
        return files.canonicalStateContextFile();
    }

    public Path validationSystemPromptFile() {
        return files.validationSystemPromptFile();
    }

    public Path validationRequestTemplateFile() {
        return files.validationRequestTemplateFile();
    }

    public Path canonicalStateFile() {
        return files.canonicalStateFile();
    }

    public Path historyFile() {
        return files.historyFile();
    }

    public Path legacyHistoryFile() {
        return files.legacyHistoryFile();
    }

    public int maxRecentTurns() {
        return conversation.maxRecentTurns();
    }

    public int summaryBatchMessages() {
        return conversation.summaryBatchMessages();
    }

    public int recentSummaryMaxTurns() {
        return conversation.recentSummaryMaxTurns();
    }

    public int recentSummaryBatchMessages() {
        return conversation.recentSummaryBatchMessages();
    }

    public int canonicalStateBatchMessages() {
        return conversation.canonicalStateBatchMessages();
    }

    public int requestTimeoutSeconds() {
        return execution.timeouts().requestTimeoutSeconds();
    }

    public int summaryRequestTimeoutSeconds() {
        return execution.timeouts().summaryRequestTimeoutSeconds();
    }

    public int validationRequestTimeoutSeconds() {
        return execution.timeouts().validationRequestTimeoutSeconds();
    }

    public int chatFailureThreshold() {
        return execution.resilience().chatFailureThreshold();
    }

    public int chatCooldownSeconds() {
        return execution.resilience().chatCooldownSeconds();
    }

    public int validationFailureThreshold() {
        return execution.resilience().validationFailureThreshold();
    }

    public int validationCooldownSeconds() {
        return execution.resilience().validationCooldownSeconds();
    }

    public int backgroundFailureThreshold() {
        return execution.resilience().backgroundFailureThreshold();
    }

    public int backgroundCooldownSeconds() {
        return execution.resilience().backgroundCooldownSeconds();
    }

    public boolean hideReasoningBlocks() {
        return runtimeText.hideReasoningBlocks();
    }

    public boolean validationEnabled() {
        return runtimeText.validationEnabled();
    }

    public String validationFailClosedMessage() {
        return runtimeText.validationFailClosedMessage();
    }

    public Map<String, Object> chatOptions() {
        return options.chatOptions();
    }

    public Map<String, Object> summaryOptions() {
        return options.summaryOptions();
    }

    public Map<String, Object> validationOptions() {
        return options.validationOptions();
    }

    public String continueStoryCommand() {
        return runtimeText.continueStoryCommand();
    }

    public String resetStoryCommand() {
        return runtimeText.resetStoryCommand();
    }

    public String bannerStartText() {
        return runtimeText.bannerStartText();
    }

    public String shortcutContinueHint() {
        return runtimeText.shortcutContinueHint();
    }

    public String shortcutResetHint() {
        return runtimeText.shortcutResetHint();
    }

    public String lmStudioRequestErrorText() {
        return runtimeText.lmStudioRequestErrorText();
    }

    public String processHistoryErrorText() {
        return runtimeText.processHistoryErrorText();
    }

    public String commandHelpText() {
        return runtimeText.commandHelpText();
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
            new ModelAccessConfig(
                source.requiredString("lmstudio.url"),
                source.optionalTrimmedString("model.chat"),
                source.optionalTrimmedString("model.validator")
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
            new ExecutionConfig(
                new TimeoutConfig(
                    source.requiredInt("timeout.chatSeconds"),
                    source.requiredInt("timeout.summarySeconds"),
                    source.requiredInt("timeout.validationSeconds")
                ),
                new ResilienceConfig(
                    source.requiredInt("resilience.chat.failureThreshold"),
                    source.requiredInt("resilience.chat.cooldownSeconds"),
                    source.requiredInt("resilience.validation.failureThreshold"),
                    source.requiredInt("resilience.validation.cooldownSeconds"),
                    source.requiredInt("resilience.background.failureThreshold"),
                    source.requiredInt("resilience.background.cooldownSeconds")
                )
            ),
            new RuntimeTextConfig(
                source.requiredBoolean("validation.enabled"),
                source.requiredBoolean("response.hideReasoningBlocks"),
                source.requiredString("response.validationFailClosedMessage"),
                source.requiredString("command.continueStory"),
                source.requiredString("command.resetStory"),
                source.requiredString("ui.bannerStart"),
                source.requiredString("ui.commandHelp"),
                source.requiredString("ui.shortcutContinueHint"),
                source.requiredString("ui.shortcutResetHint"),
                source.requiredString("ui.errorLmStudioRequest"),
                source.requiredString("ui.errorProcessHistory")
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
            )
        ).validate();
    }

    private record ModelAccessConfig(
        String lmStudioUrl,
        String chatModel,
        String validatorModel
    ) {}

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

    private record ExecutionConfig(
        TimeoutConfig timeouts,
        ResilienceConfig resilience
    ) {}

    private record TimeoutConfig(
        int requestTimeoutSeconds,
        int summaryRequestTimeoutSeconds,
        int validationRequestTimeoutSeconds
    ) {}

    private record ResilienceConfig(
        int chatFailureThreshold,
        int chatCooldownSeconds,
        int validationFailureThreshold,
        int validationCooldownSeconds,
        int backgroundFailureThreshold,
        int backgroundCooldownSeconds
    ) {}

    private record RuntimeTextConfig(
        boolean validationEnabled,
        boolean hideReasoningBlocks,
        String validationFailClosedMessage,
        String continueStoryCommand,
        String resetStoryCommand,
        String bannerStartText,
        String commandHelpText,
        String shortcutContinueHint,
        String shortcutResetHint,
        String lmStudioRequestErrorText,
        String processHistoryErrorText
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
}
