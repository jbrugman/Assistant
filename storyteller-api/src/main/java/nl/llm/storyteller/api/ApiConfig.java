package nl.llm.storyteller.api;

import java.nio.file.Path;
import java.time.Duration;

public record ApiConfig(
  String host,
  int port,
  Path databasePath,
  String databaseUsername,
  String databasePassword,
  Duration sessionInactivityTimeout
) {
  public ApiConfig {
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("API host must not be blank.");
    }
    if (port < 0 || port > 65_535) {
      throw new IllegalArgumentException("API port must be between 0 and 65535.");
    }
    if (databasePath == null) {
      throw new IllegalArgumentException("Database path must not be null.");
    }
    if (sessionInactivityTimeout == null || sessionInactivityTimeout.isZero()
      || sessionInactivityTimeout.isNegative()) {
      throw new IllegalArgumentException("Session inactivity timeout must be positive.");
    }
    databaseUsername = databaseUsername == null ? "" : databaseUsername;
    databasePassword = databasePassword == null ? "" : databasePassword;
  }

  public static ApiConfig load() {
    return ApiConfigLoader.load();
  }

  public String databaseUrl() {
    return "jdbc:h2:file:" + databasePath.toAbsolutePath().normalize();
  }
}
