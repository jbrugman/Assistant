package nl.llm.storyteller.db;

import nl.llm.storyteller.core.service.TextMemory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class JdbcTextMemory implements TextMemory {
  public enum Type {
    SUMMARY(
      "session_memory.summary_content",
      ApplicationStorageQueries.SELECT_SUMMARY,
      ApplicationStorageQueries.UPDATE_SUMMARY
    ),
    RECENT_SUMMARY(
      "session_memory.recent_summary_content",
      ApplicationStorageQueries.SELECT_RECENT_SUMMARY,
      ApplicationStorageQueries.UPDATE_RECENT_SUMMARY
    ),
    CANONICAL_STATE(
      "session_memory.canonical_state_content",
      ApplicationStorageQueries.SELECT_CANONICAL_STATE,
      ApplicationStorageQueries.UPDATE_CANONICAL_STATE
    );

    private final String field;
    private final String selectSql;
    private final String updateSql;

    Type(String field, String selectSql, String updateSql) {
      this.field = field;
      this.selectSql = selectSql;
      this.updateSql = updateSql;
    }
  }

  private final Database database;
  private final String sessionId;
  private final String field;
  private final String selectSql;
  private final String updateSql;

  public JdbcTextMemory(Database database, String sessionId, Type type) {
    this.database = database;
    this.sessionId = sessionId;
    this.field = type.field;
    this.selectSql = type.selectSql;
    this.updateSql = type.updateSql;
  }

  @Override
  public String load() {
    try (Connection connection = database.openConnection();
         PreparedStatement statement = connection.prepareStatement(selectSql)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new SQLException("Session memory does not exist for session " + sessionId + ".");
        }
        String content = database.encryption().decrypt(sessionId, field, resultSet.getString(1));
        return content == null ? "" : content;
      }
    } catch (SQLException ex) {
      throw new DatabaseException("Could not load CLI derived memory.", ex);
    }
  }

  @Override
  public void save(String content) {
    try (Connection connection = database.openConnection();
         PreparedStatement statement = connection.prepareStatement(updateSql)) {
      statement.setString(1, database.encryption().encrypt(sessionId, field, content));
      statement.setString(2, sessionId);
      if (statement.executeUpdate() != 1) {
        throw new SQLException("Session memory does not exist for session " + sessionId + ".");
      }
    } catch (SQLException ex) {
      throw new DatabaseException("Could not save CLI derived memory.", ex);
    }
  }
}
