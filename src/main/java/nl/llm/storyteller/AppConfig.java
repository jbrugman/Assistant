package nl.llm.storyteller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

final class AppConfig {
    private static final Path BASE_DIR = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    private static final String CONFIG_RESOURCE = "/systemprompts/application.config";
    private static final Path LOCAL_CONFIG_FILE = BASE_DIR.resolve("systemprompts/application.config");
    private static final String NATIVE_IMAGE_KIND_PROPERTY = "org.graalvm.nativeimage.kind";
    private static final String NATIVE_IMAGE_KIND_EXECUTABLE = "executable";
    private static final String RUNTIME_OVERRIDE_FILE_NAME = "application.config";
    private static final String OPTION_TEMPERATURE = "temperature";
    private static final String OPTION_TOP_K = "top_k";
    private static final String OPTION_TOP_P = "top_p";
    private static final String OPTION_MIN_P = "min_p";
    private static final String OPTION_REPEAT_PENALTY = "repeat_penalty";

    private final Models models;
    private final FilesConfig files;
    private final ConversationConfig conversation;
    private final TimeoutConfig timeouts;
    private final ResponseConfig response;
    private final OptionsConfig options;
    private final String lmStudioUrl;
    private final UiTextConfig uiText;

    private AppConfig(
        String lmStudioUrl,
        Models models,
        FilesConfig files,
        ConversationConfig conversation,
        TimeoutConfig timeouts,
        ResponseConfig response,
        OptionsConfig options,
        UiTextConfig uiText
    ) {
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
        Properties mergedProperties = loadRequiredPropertiesFromResource(CONFIG_RESOURCE);
        mergeProperties(mergedProperties, loadOptionalProperties(LOCAL_CONFIG_FILE, BASE_DIR));

        Path runtimeOverrideFile = findRuntimeOverrideFile();
        if (runtimeOverrideFile != null) {
            mergeProperties(mergedProperties, loadOptionalProperties(runtimeOverrideFile, runtimeOverrideFile.getParent()));
        }

        return new AppConfig(
            getRequiredString(mergedProperties, "lmstudio.url"),
            new Models(
                getRequiredString(mergedProperties, "model.chat"),
                getRequiredString(mergedProperties, "model.validator")
            ),
            new FilesConfig(
                resolveRequiredPath(mergedProperties, "file.systemPrompt"),
                resolveRequiredPath(mergedProperties, "file.rules"),
                resolveRequiredPath(mergedProperties, "file.fixedProtagonists"),
                resolveRequiredPath(mergedProperties, "file.fixedProtagonistsContext"),
                resolveRequiredPath(mergedProperties, "file.summarySystemPrompt"),
                resolveRequiredPath(mergedProperties, "file.summaryContext"),
                resolveRequiredPath(mergedProperties, "file.summary"),
                resolveRequiredPath(mergedProperties, "file.recentSummarySystemPrompt"),
                resolveRequiredPath(mergedProperties, "file.recentSummaryContext"),
                resolveRequiredPath(mergedProperties, "file.recentSummary"),
                resolveRequiredPath(mergedProperties, "file.canonicalStateSystemPrompt"),
                resolveRequiredPath(mergedProperties, "file.canonicalStateContext"),
                resolveRequiredPath(mergedProperties, "file.validationSystemPrompt"),
                resolveRequiredPath(mergedProperties, "file.validationRequestTemplate"),
                resolveRequiredPath(mergedProperties, "file.canonicalState"),
                resolveRequiredPath(mergedProperties, "file.history"),
                resolveRequiredPath(mergedProperties, "file.legacyHistory")
            ),
            new ConversationConfig(
                getRequiredInt(mergedProperties, "chat.maxRecentTurns"),
                getRequiredInt(mergedProperties, "recentSummary.maxRecentTurns"),
                getRequiredInt(mergedProperties, "summary.batchMessages"),
                getRequiredInt(mergedProperties, "recentSummary.batchMessages"),
                getRequiredInt(mergedProperties, "canonicalState.batchMessages")
            ),
            new TimeoutConfig(
                getRequiredInt(mergedProperties, "timeout.chatSeconds"),
                getRequiredInt(mergedProperties, "timeout.summarySeconds"),
                getRequiredInt(mergedProperties, "timeout.validationSeconds")
            ),
            new ResponseConfig(
                getRequiredBoolean(mergedProperties, "validation.enabled"),
                getRequiredBoolean(mergedProperties, "response.hideReasoningBlocks"),
                getRequiredString(mergedProperties, "response.validationFailClosedMessage")
            ),
            new OptionsConfig(
                linkedMapOf(
                    OPTION_TEMPERATURE, getRequiredDouble(mergedProperties, "chat.temperature"),
                    OPTION_TOP_K, getRequiredInt(mergedProperties, "chat.topK"),
                    OPTION_TOP_P, getRequiredDouble(mergedProperties, "chat.topP"),
                    OPTION_MIN_P, getRequiredDouble(mergedProperties, "chat.minP"),
                    OPTION_REPEAT_PENALTY, getRequiredDouble(mergedProperties, "chat.repeatPenalty")
                ),
                linkedMapOf(
                    OPTION_TEMPERATURE, getRequiredDouble(mergedProperties, "summary.temperature"),
                    OPTION_TOP_P, getRequiredDouble(mergedProperties, "summary.topP")
                ),
                linkedMapOf(
                    OPTION_TEMPERATURE, getRequiredDouble(mergedProperties, "validation.temperature"),
                    OPTION_TOP_P, getRequiredDouble(mergedProperties, "validation.topP")
                )
            ),
            new UiTextConfig(
                getRequiredString(mergedProperties, "command.continueStory"),
                getRequiredString(mergedProperties, "command.resetStory"),
                getRequiredString(mergedProperties, "ui.bannerStart"),
                getRequiredString(mergedProperties, "ui.shortcutContinueHint"),
                getRequiredString(mergedProperties, "ui.shortcutResetHint"),
                getRequiredString(mergedProperties, "ui.errorLmStudioRequest"),
                getRequiredString(mergedProperties, "ui.errorProcessHistory")
            )
        ).validate();
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
        return BASE_DIR;
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

    private static Properties loadRequiredPropertiesFromResource(String resourcePath) {
        Properties properties = new Properties();
        try (var input = AppConfig.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing required configuration resource: " + resourcePath);
            }
            properties.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read configuration resource " + resourcePath, ex);
        }
    }

    private static Properties loadOptionalProperties(Path path, Path relativeBaseDir) {
        Properties properties = new Properties();
        if (!Files.exists(path)) {
            return properties;
        }

        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
            absolutizeFileProperties(properties, relativeBaseDir);
            return properties;
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read configuration from " + path, ex);
        }
    }

    private static Path findRuntimeOverrideFile() {
        if (!NATIVE_IMAGE_KIND_EXECUTABLE.equals(System.getProperty(NATIVE_IMAGE_KIND_PROPERTY))) {
            return null;
        }

        String command = ProcessHandle.current().info().command().orElse(null);
        if (command == null || command.isBlank()) {
            return null;
        }

        Path executablePath = Path.of(command).toAbsolutePath().normalize();
        Path parent = executablePath.getParent();
        if (parent == null) {
            return null;
        }

        Path overrideFile = parent.resolve(RUNTIME_OVERRIDE_FILE_NAME);
        return Files.exists(overrideFile) ? overrideFile : null;
    }

    private static void mergeProperties(Properties target, Properties source) {
        for (String name : source.stringPropertyNames()) {
            target.setProperty(name, source.getProperty(name));
        }
    }

    private static void absolutizeFileProperties(Properties properties, Path relativeBaseDir) {
        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith("file.")) {
                continue;
            }

            Path path = Path.of(properties.getProperty(name).trim());
            if (path.isAbsolute()) {
                continue;
            }
            properties.setProperty(name, relativeBaseDir.resolve(path).normalize().toString());
        }
    }

    private static Path resolveRequiredPath(Properties properties, String key) {
        String configured = getRequiredString(properties, key);
        Path path = Path.of(configured);
        if (path.isAbsolute()) {
            return path;
        }
        return BASE_DIR.resolve(path).normalize();
    }

    private static String getRequiredString(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required configuration key: " + key);
        }
        return value.trim();
    }

    private static int getRequiredInt(Properties properties, String key) {
        return Integer.parseInt(getRequiredString(properties, key));
    }

    private static double getRequiredDouble(Properties properties, String key) {
        return Double.parseDouble(getRequiredString(properties, key));
    }

    private static boolean getRequiredBoolean(Properties properties, String key) {
        String value = getRequiredString(properties, key);
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        throw new IllegalArgumentException("Configuration key must be true or false: " + key);
    }

    private static Map<String, Object> linkedMapOf(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            values.put((String) entries[i], entries[i + 1]);
        }
        return values;
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
