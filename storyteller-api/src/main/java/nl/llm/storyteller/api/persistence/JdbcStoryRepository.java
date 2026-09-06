package nl.llm.storyteller.api.persistence;

import nl.llm.storyteller.core.model.Message;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static nl.llm.storyteller.api.persistence.StoryQueries.INSERT_MESSAGE;
import static nl.llm.storyteller.api.persistence.StoryQueries.DELETE_MESSAGE;
import static nl.llm.storyteller.api.persistence.StoryQueries.SELECT_LAST_MESSAGE_INDEX;
import static nl.llm.storyteller.api.persistence.StoryQueries.SELECT_MESSAGES;
import static nl.llm.storyteller.api.persistence.StoryQueries.SELECT_RECENT_MESSAGES;
import static nl.llm.storyteller.api.persistence.StoryQueries.UPDATE_SESSION_AFTER_TURN;

public final class JdbcStoryRepository implements StoryRepository {
  private final Database database;

  public JdbcStoryRepository(Database database) {
    this.database = database;
  }

  @Override
  public List<Message> loadMessages(String sessionId) {
    try (Connection connection = database.openConnection();
         PreparedStatement statement = connection.prepareStatement(SELECT_MESSAGES)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        return readMessages(resultSet);
      }
    } catch (SQLException ex) {
      throw new DatabaseException("Could not load story messages for session " + sessionId + ".", ex);
    }
  }

  @Override
  public List<Message> loadRecentMessages(String sessionId, int maximumMessages) {
    try (Connection connection = database.openConnection();
         PreparedStatement statement = connection.prepareStatement(SELECT_RECENT_MESSAGES)) {
      statement.setString(1, sessionId);
      statement.setInt(2, maximumMessages);
      try (ResultSet resultSet = statement.executeQuery()) {
        List<Message> messages = new ArrayList<>(readMessages(resultSet));
        Collections.reverse(messages);
        return List.copyOf(messages);
      }
    } catch (SQLException ex) {
      throw new DatabaseException("Could not load story messages for session " + sessionId + ".", ex);
    }
  }

  private List<Message> readMessages(ResultSet resultSet) throws SQLException {
    List<Message> messages = new ArrayList<>();
    while (resultSet.next()) {
      messages.add(new Message(resultSet.getString("message_role"), resultSet.getString("content")));
    }
    return List.copyOf(messages);
  }

  @Override
  public StoryTurnRecord appendTurn(
    String sessionId,
    String userInput,
    String assistantResponse,
    Instant updatedAt
  ) {
    try (Connection connection = database.openConnection()) {
      connection.setAutoCommit(false);
      return appendInTransaction(connection, sessionId, userInput, assistantResponse, updatedAt);
    } catch (SQLException ex) {
      throw new DatabaseException("Could not append story turn for session " + sessionId + ".", ex);
    }
  }

  @Override
  public boolean undoLastTurn(String sessionId, Instant updatedAt) {
    try (Connection connection = database.openConnection()) {
      connection.setAutoCommit(false);
      return undoInTransaction(connection, sessionId, updatedAt);
    } catch (SQLException ex) {
      throw new DatabaseException("Could not undo the last story turn for session " + sessionId + ".", ex);
    }
  }

  private boolean undoInTransaction(Connection connection, String sessionId, Instant updatedAt)
    throws SQLException {
    try {
      List<Integer> messageIndexes = lastCompleteTurnIndexes(connection, sessionId);
      if (messageIndexes.isEmpty()) {
        connection.rollback();
        return false;
      }
      for (int messageIndex : messageIndexes) {
        deleteMessage(connection, sessionId, messageIndex);
      }
      updateSession(connection, sessionId, updatedAt);
      connection.commit();
      return true;
    } catch (SQLException ex) {
      rollback(connection, ex);
      throw ex;
    }
  }

  private List<Integer> lastCompleteTurnIndexes(Connection connection, String sessionId) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(SELECT_RECENT_MESSAGES)) {
      statement.setString(1, sessionId);
      statement.setInt(2, 2);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next() || !"assistant".equals(resultSet.getString("message_role"))) {
          return List.of();
        }
        int assistantIndex = resultSet.getInt("message_index");
        if (!resultSet.next() || !"user".equals(resultSet.getString("message_role"))) {
          return List.of();
        }
        return List.of(assistantIndex, resultSet.getInt("message_index"));
      }
    }
  }

  private void deleteMessage(Connection connection, String sessionId, int messageIndex) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(DELETE_MESSAGE)) {
      statement.setString(1, sessionId);
      statement.setInt(2, messageIndex);
      statement.executeUpdate();
    }
  }

  private StoryTurnRecord appendInTransaction(
    Connection connection,
    String sessionId,
    String userInput,
    String assistantResponse,
    Instant updatedAt
  ) throws SQLException {
    try {
      int userMessageIndex = nextMessageIndex(connection, sessionId);
      insertMessage(connection, sessionId, userMessageIndex, "user", userInput);
      int assistantMessageIndex = userMessageIndex + 1;
      insertMessage(connection, sessionId, assistantMessageIndex, "assistant", assistantResponse);
      updateSession(connection, sessionId, updatedAt);
      connection.commit();
      return new StoryTurnRecord(userMessageIndex, assistantMessageIndex);
    } catch (SQLException ex) {
      rollback(connection, ex);
      throw ex;
    }
  }

  private int nextMessageIndex(Connection connection, String sessionId) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(SELECT_LAST_MESSAGE_INDEX)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        int lastIndex = resultSet.getInt("last_message_index");
        return resultSet.wasNull() ? 0 : lastIndex + 1;
      }
    }
  }

  private void insertMessage(
    Connection connection,
    String sessionId,
    int messageIndex,
    String role,
    String content
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(INSERT_MESSAGE)) {
      statement.setString(1, sessionId);
      statement.setInt(2, messageIndex);
      statement.setString(3, role);
      statement.setString(4, content);
      statement.executeUpdate();
    }
  }

  private void updateSession(Connection connection, String sessionId, Instant updatedAt) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(UPDATE_SESSION_AFTER_TURN)) {
      statement.setTimestamp(1, Timestamp.from(updatedAt));
      statement.setString(2, sessionId);
      if (statement.executeUpdate() != 1) {
        throw new SQLException("Session no longer exists.");
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
