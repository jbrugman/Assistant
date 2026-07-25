package nl.llm.storyteller;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class AppConfigLoader {
    private static final Path DEFAULT_BASE_DIR = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    private static final String CONFIG_RESOURCE = "/systemprompts/application.config";
    private static final String NATIVE_IMAGE_KIND_PROPERTY = "org.graalvm.nativeimage.kind";
    private static final String NATIVE_IMAGE_KIND_EXECUTABLE = "executable";
    private static final String RUNTIME_OVERRIDE_FILE_NAME = "application.config";

    private AppConfigLoader() {
    }

    static AppConfig load() {
        return load(DEFAULT_BASE_DIR, findRuntimeOverrideFile());
    }

    static AppConfig load(Path baseDir, Path runtimeOverrideFile) {
        Path normalizedBaseDir = baseDir.toAbsolutePath().normalize();
        Properties mergedProperties = loadRequiredConfigProperties();
        mergeProperties(
            mergedProperties,
            loadOptionalProperties(normalizedBaseDir.resolve("systemprompts/application.config"), normalizedBaseDir)
        );

        if (runtimeOverrideFile != null) {
            Path normalizedOverride = runtimeOverrideFile.toAbsolutePath().normalize();
            Path relativeBaseDir = normalizedOverride.getParent();
            if (relativeBaseDir == null) {
                relativeBaseDir = normalizedBaseDir;
            }
            mergeProperties(mergedProperties, loadOptionalProperties(normalizedOverride, relativeBaseDir));
        }

        return AppConfig.from(new AppConfigSource(normalizedBaseDir, mergedProperties));
    }

    private static Properties loadRequiredConfigProperties() {
        Properties properties = new Properties();
        try (var input = AppConfigLoader.class.getResourceAsStream(CONFIG_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing required configuration resource: " + CONFIG_RESOURCE);
            }
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read configuration resource " + CONFIG_RESOURCE, ex);
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

    private static void mergeProperties(Properties target, Properties source) {
        for (String name : source.stringPropertyNames()) {
            target.setProperty(name, source.getProperty(name));
        }
    }

    private static void absolutizeFileProperties(Properties properties, Path relativeBaseDir) {
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith("file.")) {
                Path path = Path.of(properties.getProperty(name).trim());
                if (!path.isAbsolute()) {
                    properties.setProperty(name, relativeBaseDir.resolve(path).normalize().toString());
                }
            }
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
}
