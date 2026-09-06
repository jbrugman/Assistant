package nl.llm.storyteller.api.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import static nl.llm.storyteller.api.persistence.SessionQueries.INSERT_SESSION;

final class SessionPersistenceSupport {
  private SessionPersistenceSupport() { }

  static void insertSession(Connection connection, SessionRecord session) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(INSERT_SESSION)) {
      statement.setString(1, session.sessionId());
      statement.setString(2, session.title());
      statement.setTimestamp(3, Timestamp.from(session.createdAt()));
      statement.setTimestamp(4, Timestamp.from(session.updatedAt()));
      statement.setTimestamp(5, Timestamp.from(session.lastAccessedAt()));
      statement.setTimestamp(6, Timestamp.from(session.expiresAt()));
      statement.setBoolean(7, session.infinite());
      statement.executeUpdate();
    }
  }

  static void insertSessionId(Connection connection, String sql, String sessionId) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sessionId);
      statement.executeUpdate();
    }
  }
}
