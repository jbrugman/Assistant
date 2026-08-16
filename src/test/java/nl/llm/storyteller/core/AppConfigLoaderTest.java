package nl.llm.storyteller.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigLoaderTest {
    @Test
    @DisplayName("""
        Given no local configuration override,
        When the application config is loaded for a base directory,
        Then bundled defaults should still resolve file paths relative to that base directory
        """)
    void shouldResolveBundledFileDefaultsRelativeToBaseDirectory() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-config-base");

        AppConfig config = AppConfigLoader.load(baseDirectory, null);

        assertEquals(baseDirectory.toAbsolutePath().normalize(), config.baseDir());
        assertTrue(config.chatModel().isBlank());
        assertTrue(config.validatorModel().isBlank());
        assertEquals(5, config.cacheBusterInterval());
        assertEquals(
            baseDirectory.resolve("systemprompts/systemprompt.md").normalize(),
            config.systemPromptFile()
        );
        assertEquals(
            baseDirectory.resolve("memory/history.json").normalize(),
            config.historyFile()
        );
    }

    @Test
    @DisplayName("""
        Given a local application.config override with relative file paths,
        When the application config is loaded,
        Then override values should win and relative file paths should resolve from the application base directory
        """)
    void shouldPreferLocalOverrideAndResolveItsRelativePathsFromBaseDirectory() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-config-override");
        Path configFile = baseDirectory.resolve("systemprompts/application.config");
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, """
            lmstudio.url=http://localhost:9999/v1/chat/completions
            model.chat=test-chat-model
            file.systemPrompt=overrides/story.md
            chat.maxRecentTurns=3
            cacheBuster.interval=0
            """);

        AppConfig config = AppConfigLoader.load(baseDirectory, null);

        assertEquals("http://localhost:9999/v1/chat/completions", config.lmStudioUrl());
        assertEquals("test-chat-model", config.chatModel());
        assertEquals(3, config.maxRecentTurns());
        assertEquals(0, config.cacheBusterInterval());
        assertEquals(
            baseDirectory.resolve("overrides/story.md").normalize(),
            config.systemPromptFile()
        );
    }

    @Test
    @DisplayName("""
        Given a runtime override file next to the executable with relative file paths,
        When the application config is loaded,
        Then the runtime override should take precedence and resolve relative file paths from the executable directory
        """)
    void shouldPreferRuntimeOverrideAndResolveItsRelativePathsFromExecutableDirectory() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-config-runtime-base");
        Path runtimeDirectory = Files.createTempDirectory("storyteller-config-runtime-dir");
        Path runtimeOverride = runtimeDirectory.resolve("application.config");
        Files.writeString(runtimeOverride, """
            model.validator=test-validator-model
            file.rules=runtime/rules.md
            """);

        AppConfig config = AppConfigLoader.load(baseDirectory, runtimeOverride);

        assertEquals("test-validator-model", config.validatorModel());
        assertEquals(
            runtimeDirectory.resolve("runtime/rules.md").normalize(),
            config.rulesFile()
        );
    }

    @Test
    @DisplayName("""
        Given a local application.config override that leaves model settings blank,
        When the application config is loaded,
        Then the blank values should be preserved so the backend can choose the active default model
        """)
    void shouldAllowBlankModelConfiguration() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-config-blank-model");
        Path configFile = baseDirectory.resolve("systemprompts/application.config");
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, """
            model.chat=
            model.validator=
            """);

        AppConfig config = AppConfigLoader.load(baseDirectory, null);

        assertTrue(config.chatModel().isBlank());
        assertTrue(config.validatorModel().isBlank());
    }
}
