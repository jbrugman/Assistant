package nl.llm.storyteller;

import java.nio.file.Path;
import java.util.Properties;

final class AppConfigSource {
    private final Path baseDir;
    private final Properties properties;

    AppConfigSource(Path baseDir, Properties properties) {
        this.baseDir = baseDir;
        this.properties = properties;
    }

    Path baseDir() {
        return baseDir;
    }

    String requiredString(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required configuration key: " + key);
        }
        return value.trim();
    }

    String optionalTrimmedString(String key) {
        String value = properties.getProperty(key);
        return value == null ? "" : value.trim();
    }

    int requiredInt(String key) {
        return Integer.parseInt(requiredString(key));
    }

    double requiredDouble(String key) {
        return Double.parseDouble(requiredString(key));
    }

    boolean requiredBoolean(String key) {
        String value = requiredString(key);
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        throw new IllegalArgumentException("Configuration key must be true or false: " + key);
    }

    Path requiredPath(String key) {
        Path path = Path.of(requiredString(key));
        if (path.isAbsolute()) {
            return path;
        }
        return baseDir.resolve(path).normalize();
    }
}
