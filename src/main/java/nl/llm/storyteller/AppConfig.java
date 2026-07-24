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
    private static final Path CONFIG_FILE = BASE_DIR.resolve("systemprompts/application.config");
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

    private AppConfig(
        String lmStudioUrl,
        Models models,
        FilesConfig files,
        ConversationConfig conversation,
        TimeoutConfig timeouts,
        ResponseConfig response,
        OptionsConfig options
    ) {
        this.lmStudioUrl = lmStudioUrl;
        this.models = models;
        this.files = files;
        this.conversation = conversation;
        this.timeouts = timeouts;
        this.response = response;
        this.options = options;
    }

    static AppConfig load() {
        Properties baseProperties = loadRequiredProperties(CONFIG_FILE);
        Path runtimeOverrideFile = findRuntimeOverrideFile();
        Properties overrideProperties = runtimeOverrideFile == null ? new Properties() : loadOptionalProperties(runtimeOverrideFile);
        Path overrideBaseDir = runtimeOverrideFile == null ? BASE_DIR : runtimeOverrideFile.getParent();

        return new AppConfig(
            getRequiredString(baseProperties, overrideProperties, "lmstudio.url"),
            new Models(
                getRequiredString(baseProperties, overrideProperties, "model.chat"),
                getRequiredString(baseProperties, overrideProperties, "model.validator")
            ),
            new FilesConfig(
                resolveRequiredPath(baseProperties, overrideProperties, overrideBaseDir, "file.systemPrompt"),
                resolveRequiredPath(baseProperties, overrideProperties, overrideBaseDir, "file.rules"),
                resolveRequiredPath(baseProperties, overrideProperties, overrideBaseDir, "file.fixedProtagonists"),
                resolveRequiredPath(baseProperties, overrideProperties, overrideBaseDir, "file.summarySystemPrompt"),
                resolveRequiredPath(baseProperties, overrideProperties, overrideBaseDir, "file.summary"),
                resolveRequiredPath(baseProperties, overrideProperties, overrideBaseDir, "file.recentSummarySystemPrompt"),
                resolveRequiredPath(baseProperties, overrideProperties, overrideBaseDir, "file.recentSummary"),
                resolveRequiredPath(baseProperties, overrideProperties, overrideBaseDir, "file.canonicalStateSystemPrompt"),
                resolveRequiredPath(baseProperties, overrideProperties, overrideBaseDir, "file.canonicalState"),
                resolveRequiredPath(baseProperties, overrideProperties, overrideBaseDir, "file.history"),
                resolveRequiredPath(baseProperties, overrideProperties, overrideBaseDir, "file.legacyHistory")
            ),
            new ConversationConfig(
                getRequiredInt(baseProperties, overrideProperties, "chat.maxRecentTurns"),
                getRequiredInt(baseProperties, overrideProperties, "recentSummary.maxRecentTurns"),
                getRequiredInt(baseProperties, overrideProperties, "summary.batchMessages"),
                getRequiredInt(baseProperties, overrideProperties, "recentSummary.batchMessages"),
                getRequiredInt(baseProperties, overrideProperties, "canonicalState.batchMessages")
            ),
            new TimeoutConfig(
                getRequiredInt(baseProperties, overrideProperties, "timeout.chatSeconds"),
                getRequiredInt(baseProperties, overrideProperties, "timeout.summarySeconds"),
                getRequiredInt(baseProperties, overrideProperties, "timeout.validationSeconds")
            ),
            new ResponseConfig(
                getRequiredBoolean(baseProperties, overrideProperties, "validation.enabled"),
                getRequiredBoolean(baseProperties, overrideProperties, "response.hideReasoningBlocks"),
                getRequiredString(baseProperties, overrideProperties, "response.validationFailClosedMessage")
            ),
            new OptionsConfig(
                linkedMapOf(
                    OPTION_TEMPERATURE, getRequiredDouble(baseProperties, overrideProperties, "chat.temperature"),
                    OPTION_TOP_K, getRequiredInt(baseProperties, overrideProperties, "chat.topK"),
                    OPTION_TOP_P, getRequiredDouble(baseProperties, overrideProperties, "chat.topP"),
                    OPTION_MIN_P, getRequiredDouble(baseProperties, overrideProperties, "chat.minP"),
                    OPTION_REPEAT_PENALTY, getRequiredDouble(baseProperties, overrideProperties, "chat.repeatPenalty")
                ),
                linkedMapOf(
                    OPTION_TEMPERATURE, getRequiredDouble(baseProperties, overrideProperties, "summary.temperature"),
                    OPTION_TOP_P, getRequiredDouble(baseProperties, overrideProperties, "summary.topP")
                ),
                linkedMapOf(
                    OPTION_TEMPERATURE, getRequiredDouble(baseProperties, overrideProperties, "validation.temperature"),
                    OPTION_TOP_P, getRequiredDouble(baseProperties, overrideProperties, "validation.topP")
                )
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

    Path summaryFile() {
        return files.summaryFile();
    }

    Path recentSummarySystemPromptFile() {
        return files.recentSummarySystemPromptFile();
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

    private static Properties loadRequiredProperties(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalStateException("Missing required configuration file: " + path);
        }
        return loadOptionalProperties(path);
    }

    private static Properties loadOptionalProperties(Path path) {
        Properties properties = new Properties();
        if (!Files.exists(path)) {
            return properties;
        }

        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
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

    private static Path resolveRequiredPath(
        Properties baseProperties,
        Properties overrideProperties,
        Path overrideBaseDir,
        String key
    ) {
        String configured = getRequiredString(baseProperties, overrideProperties, key);
        Path path = Path.of(configured);
        if (path.isAbsolute()) {
            return path;
        }

        Path basePath = overrideProperties.containsKey(key) ? overrideBaseDir : BASE_DIR;
        return basePath.resolve(path).normalize();
    }

    private static String getRequiredString(Properties baseProperties, Properties overrideProperties, String key) {
        String value = overrideProperties.getProperty(key, baseProperties.getProperty(key));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required configuration key: " + key);
        }
        return value.trim();
    }

    private static int getRequiredInt(Properties baseProperties, Properties overrideProperties, String key) {
        return Integer.parseInt(getRequiredString(baseProperties, overrideProperties, key));
    }

    private static double getRequiredDouble(Properties baseProperties, Properties overrideProperties, String key) {
        return Double.parseDouble(getRequiredString(baseProperties, overrideProperties, key));
    }

    private static boolean getRequiredBoolean(Properties baseProperties, Properties overrideProperties, String key) {
        String value = getRequiredString(baseProperties, overrideProperties, key);
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
        Path summarySystemPromptFile,
        Path summaryFile,
        Path recentSummarySystemPromptFile,
        Path recentSummaryFile,
        Path canonicalStateSystemPromptFile,
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
}
