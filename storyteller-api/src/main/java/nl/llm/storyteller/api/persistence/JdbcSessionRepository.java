package nl.llm.storyteller.api.persistence;

import static nl.llm.storyteller.api.persistence.SessionQueries.DELETE_EXPIRED_SESSIONS;
import static nl.llm.storyteller.api.persistence.SessionQueries.DELETE_EXPIRED_SESSION_FACTS;
import static nl.llm.storyteller.api.persistence.SessionQueries.DELETE_SESSION;
import static nl.llm.storyteller.api.persistence.SessionQueries.DELETE_SESSION_FACTS;
import static nl.llm.storyteller.api.persistence.SessionQueries.INSERT_KNOWLEDGE_GRAPH;
import static nl.llm.storyteller.api.persistence.SessionQueries.INSERT_SESSION_CONFIGURATION;
import static nl.llm.storyteller.api.persistence.SessionQueries.INSERT_SESSION_MEMORY;
import static nl.llm.storyteller.api.persistence.SessionQueries.INSERT_TURN_STATE;
import static nl.llm.storyteller.api.persistence.SessionQueries.SELECT_SESSION;
import static nl.llm.storyteller.api.persistence.SessionQueries.UPDATE_ACCESS;
import static nl.llm.storyteller.api.persistence.SessionQueries.UPDATE_INFINITE;
import static nl.llm.storyteller.api.persistence.SessionPersistenceSupport.insertSession;
import static nl.llm.storyteller.api.persistence.SessionPersistenceSupport.insertSessionId;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public final class JdbcSessionRepository implements SessionRepository {
  private final Database database;

  public JdbcSessionRepository(Database database) {
    this.database = database;
  }

  @Override
  public void create(SessionRecord session) {
    try (Connection connection = database.openConnection()) {
      connection.setAutoCommit(false);
      createInTransaction(connection, session);
    } catch (SQLException ex) {
      throw new DatabaseException("Could not create session " + session.sessionId() + ".", ex);
    }
  }

  private void createInTransaction(Connection connection, SessionRecord session) throws SQLException {
    try {
      insertSession(connection, session);
      insertDefaults(connection, session.sessionId());
      connection.commit();
    } catch (SQLException ex) {
      rollback(connection, ex);
      throw ex;
    }
  }

  @Override
  public Optional<SessionRecord> findById(String sessionId) {
    try (Connection connection = database.openConnection();
         PreparedStatement statement = connection.prepareStatement(SELECT_SESSION)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? Optional.of(readSession(resultSet)) : Optional.empty();
      }
    } catch (SQLException ex) {
      throw new DatabaseException("Could not load session " + sessionId + ".", ex);
    }
  }

  @Override
  public boolean refreshAccess(String sessionId, Instant accessedAt, Instant expiresAt) {
    try (Connection connection = database.openConnection();
         PreparedStatement statement = connection.prepareStatement(UPDATE_ACCESS)) {
      statement.setTimestamp(1, Timestamp.from(accessedAt));
      statement.setTimestamp(2, Timestamp.from(expiresAt));
      statement.setString(3, sessionId);
      return statement.executeUpdate() == 1;
    } catch (SQLException ex) {
      throw new DatabaseException("Could not refresh session " + sessionId + ".", ex);
    }
  }

  @Override
  public boolean setInfinite(String sessionId, boolean infinite, Instant expiresAt) {
    try (Connection connection = database.openConnection();
         PreparedStatement statement = connection.prepareStatement(UPDATE_INFINITE)) {
      statement.setBoolean(1, infinite);
      statement.setTimestamp(2, Timestamp.from(expiresAt));
      statement.setString(3, sessionId);
      return statement.executeUpdate() == 1;
    } catch (SQLException ex) {
      throw new DatabaseException("Could not change session expiration.", ex);
    }
  }

  @Override
  public void delete(String sessionId) {
    deleteWithDependencies(
      DELETE_SESSION_FACTS,
      DELETE_SESSION,
      statement -> statement.setString(1, sessionId)
    );
  }

  @Override
  public int deleteExpired(Instant expiredBefore) {
    return deleteWithDependencies(
      DELETE_EXPIRED_SESSION_FACTS,
      DELETE_EXPIRED_SESSIONS,
      statement -> statement.setTimestamp(1, Timestamp.from(expiredBefore))
    );
  }

  private void insertDefaults(Connection connection, String sessionId) throws SQLException {
    insertSessionId(connection, INSERT_SESSION_CONFIGURATION, sessionId);
    insertSessionId(connection, INSERT_SESSION_MEMORY, sessionId);
    insertSessionId(connection, INSERT_TURN_STATE, sessionId);
    insertSessionId(connection, INSERT_KNOWLEDGE_GRAPH, sessionId);
  }

  private SessionRecord readSession(ResultSet resultSet) throws SQLException {
    return new SessionRecord(
      resultSet.getString("session_id"),
      resultSet.getString("title"),
      resultSet.getTimestamp("created_at").toInstant(),
      resultSet.getTimestamp("updated_at").toInstant(),
      resultSet.getTimestamp("last_accessed_at").toInstant(),
      resultSet.getTimestamp("expires_at").toInstant(),
      resultSet.getBoolean("infinite")
    );
  }

  private int deleteWithDependencies(String dependencySql, String sessionSql, StatementBinder binder) {
    try (Connection connection = database.openConnection()) {
      connection.setAutoCommit(false);
      try {
        executeDelete(connection, dependencySql, binder);
        int deleted = executeDelete(connection, sessionSql, binder);
        connection.commit();
        return deleted;
      } catch (SQLException ex) {
        rollback(connection, ex);
        throw ex;
      }
    } catch (SQLException ex) {
      throw new DatabaseException("Could not delete session data.", ex);
    }
  }

  private int executeDelete(Connection connection, String sql, StatementBinder binder) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      binder.bind(statement);
      return statement.executeUpdate();
    }
  }

  private void rollback(Connection connection, SQLException original) {
    try {
      connection.rollback();
    } catch (SQLException rollbackFailure) {
      original.addSuppressed(rollbackFailure);
    }
  }

  @FunctionalInterface
  private interface StatementBinder {
    void bind(PreparedStatement statement) throws SQLException;
  }
}
