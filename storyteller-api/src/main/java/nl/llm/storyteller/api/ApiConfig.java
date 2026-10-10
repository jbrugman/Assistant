package nl.llm.storyteller.api;

import nl.llm.storyteller.core.config.MysqlConfig;

import java.nio.file.Path;
import java.time.Duration;

public record ApiConfig(
  String host,
  int port,
  Path databasePath,
  String databaseUsername,
  String databasePassword,
  Duration sessionInactivityTimeout,
  ApiTlsConfig tls,
  MysqlConfig mysql,
  String databaseEncryptionKey
) {
  public ApiConfig(
    String host, int port, Path databasePath, String databaseUsername, String databasePassword,
    Duration sessionInactivityTimeout, ApiTlsConfig tls, MysqlConfig mysql
  ) {
    this(host, port, databasePath, databaseUsername, databasePassword, sessionInactivityTimeout, tls, mysql, "");
  }

  public ApiConfig(
    String host, int port, Path databasePath, String databaseUsername, String databasePassword,
    Duration sessionInactivityTimeout, ApiTlsConfig tls
  ) {
    this(host, port, databasePath, databaseUsername, databasePassword, sessionInactivityTimeout, tls,
      MysqlConfig.disabled());
  }

  public ApiConfig(
    String host,
    int port,
    Path databasePath,
    String databaseUsername,
    String databasePassword,
    Duration sessionInactivityTimeout
  ) {
    this(
      host, port, databasePath, databaseUsername, databasePassword, sessionInactivityTimeout,
      ApiTlsConfig.disabled(databasePath.toAbsolutePath().normalize().resolveSibling("tls"))
    );
  }

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
    if (tls == null) {
      throw new IllegalArgumentException("API TLS configuration must not be null.");
    }
    if (mysql == null) {
      throw new IllegalArgumentException("MySQL configuration must not be null.");
    }
    if (mysql.enabled()) {
      databaseUsername = mysql.username();
      databasePassword = mysql.password();
    }
    databaseEncryptionKey = databaseEncryptionKey == null ? "" : databaseEncryptionKey.trim();
    databaseUsername = databaseUsername == null ? "" : databaseUsername;
    databasePassword = databasePassword == null ? "" : databasePassword;
  }

  @Override
  public String toString() {
    return "ApiConfig[host=" + host + ", port=" + port + ", databaseEncryption="
      + !databaseEncryptionKey.isBlank() + "]";
  }

  public static ApiConfig load() {
    return ApiConfigLoader.load();
  }

  public String databaseUrl() {
    return mysql.enabled() ? mysql.url() : "jdbc:h2:file:" + databasePath.toAbsolutePath().normalize();
  }
}
