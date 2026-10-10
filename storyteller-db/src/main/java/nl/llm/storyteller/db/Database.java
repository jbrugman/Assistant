package nl.llm.storyteller.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public record Database(String url, String username, String password, DatabaseEncryption encryption) {
  public Database(String url, String username, String password) {
    this(url, username, password, DatabaseEncryption.fromBase64(null));
  }

  public Database {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("Database URL must not be blank.");
    }
    username = username == null ? "" : username;
    password = password == null ? "" : password;
    java.util.Objects.requireNonNull(encryption, "encryption");
  }

  @Override
  public String toString() {
    return "Database[username=" + username + ", encryption=" + encryption.enabled() + "]";
  }

  public Connection openConnection() throws SQLException {
    return DriverManager.getConnection(url, username, password);
  }
}
