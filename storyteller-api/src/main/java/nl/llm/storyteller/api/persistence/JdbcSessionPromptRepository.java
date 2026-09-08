package nl.llm.storyteller.api.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class JdbcSessionPromptRepository implements SessionPromptRepository {
  private final Database database;

  public JdbcSessionPromptRepository(Database database) {
    this.database = database;
  }

  public void initializeMissing(SessionPrompts prompts) {
    try (Connection connection = database.openConnection()) {
      connection.setAutoCommit(false);
      initializeMissingInTransaction(connection, prompts);
    } catch (SQLException ex) {
      throw new DatabaseException("Could not initialize session prompts.", ex);
    }
  }

  @Override
  public SessionPrompts load(String sessionId) {
    try (Connection connection = database.openConnection();
         PreparedStatement statement = connection.prepareStatement(SessionPromptQueries.SELECT_PROMPTS)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        return SessionPromptPersistenceSupport.readPrompts(resultSet);
      }
    } catch (SQLException ex) {
      throw new DatabaseException("Could not load session prompts.", ex);
    }
  }

  @Override
  public void save(String sessionId, SessionPrompts prompts) {
    try (Connection connection = database.openConnection()) {
      connection.setAutoCommit(false);
      saveInTransaction(connection, sessionId, prompts);
    } catch (SQLException ex) {
      throw new DatabaseException("Could not save session prompts.", ex);
    }
  }

  private void saveInTransaction(Connection connection, String sessionId, SessionPrompts prompts)
    throws SQLException {
    try {
      update(connection, sessionId, SessionPrompts.SYSTEM_PROMPT_NAME, prompts.systemPrompt());
      update(connection, sessionId, SessionPrompts.FIXED_PROTAGONISTS_NAME, prompts.fixedProtagonists());
      update(connection, sessionId, SessionPrompts.RULES_NAME, prompts.rules());
      connection.commit();
    } catch (SQLException ex) {
      rollback(connection, ex);
      throw ex;
    }
  }

  private void initializeMissingInTransaction(Connection connection, SessionPrompts prompts)
    throws SQLException {
    try {
      insertMissing(connection, SessionPrompts.SYSTEM_PROMPT_NAME, prompts.systemPrompt());
      insertMissing(connection, SessionPrompts.FIXED_PROTAGONISTS_NAME, prompts.fixedProtagonists());
      insertMissing(connection, SessionPrompts.RULES_NAME, prompts.rules());
      connection.commit();
    } catch (SQLException ex) {
      rollback(connection, ex);
      throw ex;
    }
  }

  private void insertMissing(Connection connection, String name, String content) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(SessionPromptQueries.INSERT_MISSING_PROMPT)) {
      statement.setString(1, name);
      statement.setString(2, content);
      statement.setString(3, name);
      statement.executeUpdate();
    }
  }

  private void update(Connection connection, String sessionId, String name, String content) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(SessionPromptQueries.UPDATE_PROMPT)) {
      statement.setString(1, content);
      statement.setString(2, sessionId);
      statement.setString(3, name);
      if (statement.executeUpdate() != 1) {
        throw new SQLException("Session prompt does not exist: " + name);
      }
    }
  }

  private void rollback(Connection connection, SQLException original) {
    try {
      connection.rollback();
    } catch (SQLException rollbackFailure) {
      original.addSuppressed(rollbackFailure);
    }
  }
}
