package nl.llm.storyteller.db;

import nl.llm.storyteller.core.service.TextMemory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class JdbcTextMemory implements TextMemory {
  public enum Type {
    SUMMARY(
      ApplicationStorageQueries.SELECT_SUMMARY,
      ApplicationStorageQueries.UPDATE_SUMMARY
    ),
    RECENT_SUMMARY(
      ApplicationStorageQueries.SELECT_RECENT_SUMMARY,
      ApplicationStorageQueries.UPDATE_RECENT_SUMMARY
    ),
    CANONICAL_STATE(
      ApplicationStorageQueries.SELECT_CANONICAL_STATE,
      ApplicationStorageQueries.UPDATE_CANONICAL_STATE
    );

    private final String selectSql;
    private final String updateSql;

    Type(String selectSql, String updateSql) {
      this.selectSql = selectSql;
      this.updateSql = updateSql;
    }
  }

  private final Database database;
  private final String sessionId;
  private final String selectSql;
  private final String updateSql;

  public JdbcTextMemory(Database database, String sessionId, Type type) {
    this.database = database;
    this.sessionId = sessionId;
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
        String content = resultSet.getString(1);
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
      statement.setString(1, content);
      statement.setString(2, sessionId);
      if (statement.executeUpdate() != 1) {
        throw new SQLException("Session memory does not exist for session " + sessionId + ".");
      }
    } catch (SQLException ex) {
      throw new DatabaseException("Could not save CLI derived memory.", ex);
    }
  }
}
