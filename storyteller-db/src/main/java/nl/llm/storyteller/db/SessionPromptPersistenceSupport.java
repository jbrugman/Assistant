package nl.llm.storyteller.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

final class SessionPromptPersistenceSupport {
  private SessionPromptPersistenceSupport() {
  }

  static void insertPrompts(Connection connection, String sessionId, SessionPrompts prompts)
    throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(SessionPromptQueries.INSERT_PROMPT)) {
      statement.setString(1, sessionId);
      addPrompt(statement, SessionPrompts.SYSTEM_PROMPT_NAME, prompts.systemPrompt());
      addPrompt(statement, SessionPrompts.FIXED_PROTAGONISTS_NAME, prompts.fixedProtagonists());
      addPrompt(statement, SessionPrompts.RULES_NAME, prompts.rules());
      statement.executeBatch();
    }
  }

  private static void addPrompt(PreparedStatement statement, String name, String content) throws SQLException {
    statement.setString(2, name);
    statement.setString(3, content);
    statement.addBatch();
  }

  static SessionPrompts readPrompts(ResultSet resultSet) throws SQLException {
    Map<String, String> values = new LinkedHashMap<>();
    while (resultSet.next()) {
      values.put(resultSet.getString("override_name"), resultSet.getString("override_content"));
    }
    return new SessionPrompts(
      required(values, SessionPrompts.SYSTEM_PROMPT_NAME),
      required(values, SessionPrompts.FIXED_PROTAGONISTS_NAME),
      required(values, SessionPrompts.RULES_NAME)
    );
  }

  private static String required(Map<String, String> values, String name) throws SQLException {
    String value = values.get(name);
    if (value == null) {
      throw new SQLException("Session prompt does not exist: " + name);
    }
    return value;
  }
}
