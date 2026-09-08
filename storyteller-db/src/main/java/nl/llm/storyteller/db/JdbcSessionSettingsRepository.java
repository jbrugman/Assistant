package nl.llm.storyteller.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class JdbcSessionSettingsRepository implements SessionSettingsRepository {
  private final Database database;
  private final JdbcSessionBundleRepository bundleRepository;

  public JdbcSessionSettingsRepository(Database database) {
    this.database = database;
    this.bundleRepository = new JdbcSessionBundleRepository(database);
  }

  @Override
  public SessionSettings load(String sessionId) {
    try (Connection connection = database.openConnection()) {
      return new SessionSettings(loadPrompts(connection, sessionId), bundleRepository.loadGraph(connection, sessionId));
    } catch (SQLException ex) {
      throw new DatabaseException("Could not load settings for session " + sessionId + ".", ex);
    }
  }

  @Override
  public void save(String sessionId, SessionSettings settings) {
    try (Connection connection = database.openConnection()) {
      connection.setAutoCommit(false);
      saveInTransaction(connection, sessionId, settings);
    } catch (SQLException ex) {
      throw new DatabaseException("Could not save settings for session " + sessionId + ".", ex);
    }
  }

  private SessionPrompts loadPrompts(Connection connection, String sessionId) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(SessionPromptQueries.SELECT_PROMPTS)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        return SessionPromptPersistenceSupport.readPrompts(resultSet);
      }
    }
  }

  private void saveInTransaction(Connection connection, String sessionId, SessionSettings settings)
    throws SQLException {
    try {
      updatePrompt(connection, sessionId, SessionPrompts.SYSTEM_PROMPT_NAME, settings.prompts().systemPrompt());
      updatePrompt(
        connection,
        sessionId,
        SessionPrompts.FIXED_PROTAGONISTS_NAME,
        settings.prompts().fixedProtagonists()
      );
      updatePrompt(connection, sessionId, SessionPrompts.RULES_NAME, settings.prompts().rules());
      bundleRepository.replaceGraph(connection, sessionId, settings.knowledgeGraph());
      connection.commit();
    } catch (SQLException ex) {
      rollback(connection, ex);
      throw ex;
    }
  }

  private void updatePrompt(Connection connection, String sessionId, String name, String content) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(SessionPromptQueries.UPDATE_PROMPT)) {
      statement.setString(1, content);
      statement.setString(2, sessionId);
      statement.setString(3, name);
      requireUpdated(statement, "Session prompt does not exist: " + name);
    }
  }

  private void requireUpdated(PreparedStatement statement, String message) throws SQLException {
    if (statement.executeUpdate() != 1) {
      throw new SQLException(message);
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
