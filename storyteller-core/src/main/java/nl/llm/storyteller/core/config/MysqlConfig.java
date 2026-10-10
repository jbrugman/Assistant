package nl.llm.storyteller.core.config;

import java.util.Properties;

public record MysqlConfig(boolean enabled, String url, String username, String password) {
  public static MysqlConfig from(Properties properties) {
    String enabled = properties.getProperty("mysql", "false").trim();
    if (!"true".equalsIgnoreCase(enabled) && !"false".equalsIgnoreCase(enabled)) {
      throw new IllegalArgumentException("Configuration key must be true or false: mysql");
    }
    String url = properties.getProperty("mysql.url", "jdbc:mysql://localhost:3306/storyteller").trim();
    if (Boolean.parseBoolean(enabled) && !url.startsWith("jdbc:mysql:")) {
      throw new IllegalArgumentException("mysql.url must be a MySQL JDBC URL.");
    }
    return new MysqlConfig(
      Boolean.parseBoolean(enabled), url,
      properties.getProperty("mysql.username", "storyteller").trim(),
      properties.getProperty("mysql.password", "")
    );
  }

  public static MysqlConfig disabled() {
    return from(new Properties());
  }

  @Override
  public String toString() {
    return "MysqlConfig[enabled=" + enabled + ", url=" + url + ", username=" + username + ", password=<redacted>]";
  }
}
