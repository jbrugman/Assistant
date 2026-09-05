package nl.llm.storyteller.api.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public record Database(String url, String username, String password) {
  public Database {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("Database URL must not be blank.");
    }
    username = username == null ? "" : username;
    password = password == null ? "" : password;
  }

  public Connection openConnection() throws SQLException {
    return DriverManager.getConnection(url, username, password);
  }
}
