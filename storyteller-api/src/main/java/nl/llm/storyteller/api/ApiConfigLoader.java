package nl.llm.storyteller.api;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

final class ApiConfigLoader {
  private static final String CONFIG_RESOURCE = "/application.config";
  private static final String NATIVE_IMAGE_KIND_PROPERTY = "org.graalvm.nativeimage.kind";
  private static final String NATIVE_IMAGE_KIND_EXECUTABLE = "executable";

  private ApiConfigLoader() {
  }

  static ApiConfig load() {
    Path baseDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    return load(baseDirectory, findOverrideFile(baseDirectory));
  }

  static ApiConfig load(Path baseDirectory, Path overrideFile) {
    Path normalizedBaseDirectory = baseDirectory.toAbsolutePath().normalize();
    Properties properties = loadBundledDefaults();
    Path databaseBaseDirectory = normalizedBaseDirectory;
    if (overrideFile != null && Files.exists(overrideFile)) {
      Path normalizedOverrideFile = overrideFile.toAbsolutePath().normalize();
      merge(properties, loadProperties(normalizedOverrideFile));
      if (normalizedOverrideFile.getParent() != null) {
        databaseBaseDirectory = normalizedOverrideFile.getParent();
      }
    }

    return new ApiConfig(
      required(properties, "api.host"),
      Integer.parseInt(required(properties, "api.port")),
      resolvePath(databaseBaseDirectory, required(properties, "api.database.path")),
      required(properties, "api.database.username"),
      properties.getProperty("api.database.password", "").trim(),
      Duration.ofMinutes(Long.parseLong(required(properties, "api.sessionTimeoutMinutes")))
    );
  }

  private static Properties loadBundledDefaults() {
    Properties properties = new Properties();
    try (var input = ApiConfigLoader.class.getResourceAsStream(CONFIG_RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException("Missing required API configuration resource: " + CONFIG_RESOURCE);
      }
      properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
      return properties;
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not read API configuration resource " + CONFIG_RESOURCE, ex);
    }
  }

  private static Properties loadProperties(Path path) {
    Properties properties = new Properties();
    try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      properties.load(reader);
      return properties;
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not read API configuration from " + path, ex);
    }
  }

  private static void merge(Properties target, Properties source) {
    for (String name : source.stringPropertyNames()) {
      target.setProperty(name, source.getProperty(name));
    }
  }

  private static String required(Properties properties, String key) {
    String value = properties.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing required API configuration key: " + key);
    }
    return value.trim();
  }

  private static Path resolvePath(Path baseDirectory, String configuredPath) {
    Path path = Path.of(configuredPath);
    return path.isAbsolute() ? path.normalize() : baseDirectory.resolve(path).normalize();
  }

  private static Path findOverrideFile(Path baseDirectory) {
    if (!NATIVE_IMAGE_KIND_EXECUTABLE.equals(System.getProperty(NATIVE_IMAGE_KIND_PROPERTY))) {
      return baseDirectory.resolve("application.config");
    }
    String command = ProcessHandle.current().info().command().orElse(null);
    if (command == null || command.isBlank()) {
      return baseDirectory.resolve("application.config");
    }
    Path executableDirectory = Path.of(command).toAbsolutePath().normalize().getParent();
    return executableDirectory == null
      ? baseDirectory.resolve("application.config")
      : executableDirectory.resolve("application.config");
  }
}
