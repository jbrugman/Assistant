package nl.jbrugman.assistant;

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
    private static final Path CONFIG_FILE = BASE_DIR.resolve("assistant.properties");
    private static final String NATIVE_IMAGE_KIND_PROPERTY = "org.graalvm.nativeimage.kind";
    private static final String NATIVE_IMAGE_KIND_EXECUTABLE = "executable";
    private static final String RUNTIME_OVERRIDE_FILE_NAME = "application.config";
    private static final String DEFAULT_MODEL = "google/gemma-4-12b-qat";
    private static final String DEFAULT_VALIDATION_FAIL_CLOSED_MESSAGE = "Ja zeg... probeer maar even iets anders.";
    private static final String OPTION_TEMPERATURE = "temperature";
    private static final String OPTION_TOP_K = "top_k";
    private static final String OPTION_TOP_P = "top_p";
    private static final String OPTION_MIN_P = "min_p";
    private static final String OPTION_REPEAT_PENALTY = "repeat_penalty";
    private static final String DEFAULT_APP_MODE = "default";

    private final String lmStudioUrl;
    private final String appMode;
    private final Models models;
    private final FilesConfig files;
    private final ConversationConfig conversation;
    private final TimeoutConfig timeouts;
    private final ResponseConfig response;
    private final OptionsConfig options;

    private AppConfig(
        String lmStudioUrl,
        String appMode,
        Models models,
        FilesConfig files,
        ConversationConfig conversation,
        TimeoutConfig timeouts,
        ResponseConfig response,
        OptionsConfig options
    ) {
        this.lmStudioUrl = lmStudioUrl;
        this.appMode = appMode;
        this.models = models;
        this.files = files;
        this.conversation = conversation;
        this.timeouts = timeouts;
        this.response = response;
        this.options = options;
    }

    static AppConfig load() {
        Properties baseProperties = loadProperties(CONFIG_FILE);
        Path runtimeOverrideFile = findRuntimeOverrideFile();
        Properties overrideProperties = runtimeOverrideFile == null ? new Properties() : loadProperties(runtimeOverrideFile);
        Path overrideBaseDir = runtimeOverrideFile == null ? BASE_DIR : runtimeOverrideFile.getParent();

        return new AppConfig(
            getString(baseProperties, overrideProperties, "lmstudio.url", "http://localhost:1234/v1/chat/completions"),
            getAppMode(baseProperties, overrideProperties),
            new Models(
                getString(baseProperties, overrideProperties, "model.chat", DEFAULT_MODEL),
                getString(baseProperties, overrideProperties, "model.validator", DEFAULT_MODEL)
            ),
            new FilesConfig(
                resolvePath(baseProperties, overrideProperties, overrideBaseDir, "file.systemPrompt", "systemprompt.md"),
                resolvePath(baseProperties, overrideProperties, overrideBaseDir, "file.rules", "rules.md"),
                resolvePath(
                    baseProperties,
                    overrideProperties,
                    overrideBaseDir,
                    "file.summarySystemPrompt",
                    "summarysystemprompt.md"
                ),
                resolvePath(baseProperties, overrideProperties, overrideBaseDir, "file.summary", "summary.md"),
                resolvePath(
                    baseProperties,
                    overrideProperties,
                    overrideBaseDir,
                    "file.canonicalStateSystemPrompt",
                    "canonicalstatesystemprompt.md"
                ),
                resolvePath(baseProperties, overrideProperties, overrideBaseDir, "file.canonicalState", "canonical-state.yaml"),
                resolvePath(baseProperties, overrideProperties, overrideBaseDir, "file.history", "history.json"),
                resolvePath(baseProperties, overrideProperties, overrideBaseDir, "file.legacyHistory", "history.md")
            ),
            new ConversationConfig(
                getInt(baseProperties, overrideProperties, "chat.maxRecentTurns", 6),
                getInt(baseProperties, overrideProperties, "summary.batchMessages", 8),
                getInt(
                    baseProperties,
                    overrideProperties,
                    "canonicalState.batchMessages",
                    Math.max(1, getInt(baseProperties, overrideProperties, "summary.batchMessages", 8) / 2)
                )
            ),
            new TimeoutConfig(
                getInt(baseProperties, overrideProperties, "timeout.chatSeconds", 220),
                getInt(baseProperties, overrideProperties, "timeout.summarySeconds", 220),
                getInt(baseProperties, overrideProperties, "timeout.validationSeconds", 90)
            ),
            new ResponseConfig(
                Boolean.parseBoolean(
                    getString(baseProperties, overrideProperties, "response.hideReasoningBlocks", Boolean.toString(true))
                ),
                getString(
                    baseProperties,
                    overrideProperties,
                    "response.validationFailClosedMessage",
                    DEFAULT_VALIDATION_FAIL_CLOSED_MESSAGE
                )
            ),
            new OptionsConfig(
                linkedMapOf(
                    OPTION_TEMPERATURE, getDouble(baseProperties, overrideProperties, "chat.temperature", 1.0),
                    OPTION_TOP_K, getInt(baseProperties, overrideProperties, "chat.topK", 45),
                    OPTION_TOP_P, getDouble(baseProperties, overrideProperties, "chat.topP", 0.9),
                    OPTION_MIN_P, getDouble(baseProperties, overrideProperties, "chat.minP", 0.05),
                    OPTION_REPEAT_PENALTY, getDouble(baseProperties, overrideProperties, "chat.repeatPenalty", 1.05)
                ),
                linkedMapOf(
                    OPTION_TEMPERATURE, getDouble(baseProperties, overrideProperties, "summary.temperature", 0.2),
                    OPTION_TOP_P, getDouble(baseProperties, overrideProperties, "summary.topP", 0.9)
                ),
                linkedMapOf(
                    OPTION_TEMPERATURE, getDouble(baseProperties, overrideProperties, "validation.temperature", 0.0),
                    OPTION_TOP_P, getDouble(baseProperties, overrideProperties, "validation.topP", 0.1)
                )
            )
        );
    }

    String lmStudioUrl() {
        return lmStudioUrl;
    }

    String appMode() {
        return appMode;
    }

    boolean isStoryMode() {
        return "story".equals(appMode);
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

    Path summaryFile() {
        return files.summaryFile();
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

    private static Properties loadProperties(Path path) {
        Properties properties = new Properties();
        if (!Files.exists(path)) {
            return properties;
        }

        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException ex) {
            throw new UncheckedIOException("Kon configuratie niet lezen uit " + path, ex);
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

    private static Path resolvePath(
        Properties baseProperties,
        Properties overrideProperties,
        Path overrideBaseDir,
        String key,
        String defaultValue
    ) {
        String configured = getString(baseProperties, overrideProperties, key, defaultValue);
        Path path = Path.of(configured);
        if (path.isAbsolute()) {
            return path;
        }

        Path basePath = overrideProperties.containsKey(key) ? overrideBaseDir : BASE_DIR;
        return basePath.resolve(path).normalize();
    }

    private static String getAppMode(Properties baseProperties, Properties overrideProperties) {
        String mode = getString(baseProperties, overrideProperties, "appmode", DEFAULT_APP_MODE).toLowerCase();
        if (DEFAULT_APP_MODE.equals(mode) || "story".equals(mode)) {
            return mode;
        }
        throw new IllegalArgumentException("appmode moet 'default' of 'story' zijn.");
    }

    private static String getString(
        Properties baseProperties,
        Properties overrideProperties,
        String key,
        String defaultValue
    ) {
        return overrideProperties.getProperty(key, baseProperties.getProperty(key, defaultValue)).trim();
    }

    private static int getInt(Properties baseProperties, Properties overrideProperties, String key, int defaultValue) {
        return Integer.parseInt(getString(baseProperties, overrideProperties, key, Integer.toString(defaultValue)));
    }

    private static double getDouble(
        Properties baseProperties,
        Properties overrideProperties,
        String key,
        double defaultValue
    ) {
        return Double.parseDouble(getString(baseProperties, overrideProperties, key, Double.toString(defaultValue)));
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
        Path summarySystemPromptFile,
        Path summaryFile,
        Path canonicalStateSystemPromptFile,
        Path canonicalStateFile,
        Path historyFile,
        Path legacyHistoryFile
    ) {}

    private record ConversationConfig(int maxRecentTurns, int summaryBatchMessages, int canonicalStateBatchMessages) {}

    private record TimeoutConfig(
        int requestTimeoutSeconds,
        int summaryRequestTimeoutSeconds,
        int validationRequestTimeoutSeconds
    ) {}

    private record ResponseConfig(boolean hideReasoningBlocks, String validationFailClosedMessage) {}

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
