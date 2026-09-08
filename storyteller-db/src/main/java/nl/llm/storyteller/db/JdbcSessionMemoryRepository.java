package nl.llm.storyteller.db;

import nl.llm.storyteller.core.model.Message;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class JdbcSessionMemoryRepository implements SessionMemoryRepository {
  private final Database database;

  public JdbcSessionMemoryRepository(Database database) {
    this.database = database;
  }

  @Override
  public SessionMemory load(String sessionId) {
    try (Connection connection = database.openConnection()) {
      MemoryContent content = loadContent(connection, sessionId);
      return new SessionMemory(
        content.summary(), content.recentSummary(), content.canonicalState(), content.summaryCursor(),
        content.recentSummaryCursor(), content.canonicalStateCursor(), List.of()
      );
    } catch (SQLException ex) {
      throw new DatabaseException("Could not load memory for session " + sessionId + ".", ex);
    }
  }

  @Override
  public SessionMemory loadForUpdate(String sessionId) {
    try (Connection connection = database.openConnection()) {
      MemoryContent content = loadContent(connection, sessionId);
      return new SessionMemory(
        content.summary(), content.recentSummary(), content.canonicalState(), content.summaryCursor(),
        content.recentSummaryCursor(), content.canonicalStateCursor(), loadMessages(connection, sessionId)
      );
    } catch (SQLException ex) {
      throw new DatabaseException("Could not load memory update data for session " + sessionId + ".", ex);
    }
  }

  @Override
  public boolean updateSummary(String sessionId, SessionMemory expected, String content, int cursor) {
    return update(
      SessionMemoryQueries.UPDATE_SUMMARY, sessionId, expected.summaryCursor(), expected.summary(), content, cursor
    );
  }

  @Override
  public boolean updateRecentSummary(String sessionId, SessionMemory expected, String content, int cursor) {
    return update(
      SessionMemoryQueries.UPDATE_RECENT_SUMMARY,
      sessionId,
      expected.recentSummaryCursor(),
      expected.recentSummary(),
      content,
      cursor
    );
  }

  @Override
  public boolean updateCanonicalState(String sessionId, SessionMemory expected, String content, int cursor) {
    return update(
      SessionMemoryQueries.UPDATE_CANONICAL_STATE,
      sessionId,
      expected.canonicalStateCursor(),
      expected.canonicalState(),
      content,
      cursor
    );
  }

  private MemoryContent loadContent(Connection connection, String sessionId) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(SessionMemoryQueries.SELECT_MEMORY)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new SQLException("Session memory does not exist for session " + sessionId + ".");
        }
        return new MemoryContent(
          resultSet.getString("summary_content"),
          resultSet.getString("recent_summary_content"),
          resultSet.getString("canonical_state_content"),
          resultSet.getInt("summary_cursor"),
          resultSet.getInt("recent_summary_cursor"),
          resultSet.getInt("canonical_state_cursor")
        );
      }
    }
  }

  private List<Message> loadMessages(Connection connection, String sessionId) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(SessionMemoryQueries.SELECT_MESSAGES)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        List<Message> messages = new ArrayList<>();
        while (resultSet.next()) {
          messages.add(new Message(resultSet.getString("message_role"), resultSet.getString("content")));
        }
        return List.copyOf(messages);
      }
    }
  }

  private boolean update(
    String sql,
    String sessionId,
    int expectedCursor,
    String expectedContent,
    String content,
    int cursor
  ) {
    try (Connection connection = database.openConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, content);
      statement.setInt(2, cursor);
      statement.setString(3, sessionId);
      statement.setInt(4, expectedCursor);
      statement.setString(5, expectedContent);
      statement.setInt(6, cursor);
      statement.setString(7, sessionId);
      return statement.executeUpdate() == 1;
    } catch (SQLException ex) {
      throw new DatabaseException("Could not update memory for session " + sessionId + ".", ex);
    }
  }

  private record MemoryContent(
    String summary,
    String recentSummary,
    String canonicalState,
    int summaryCursor,
    int recentSummaryCursor,
    int canonicalStateCursor
  ) {
  }
}
