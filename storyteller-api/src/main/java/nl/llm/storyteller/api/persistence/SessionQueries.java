package nl.llm.storyteller.api.persistence;

final class SessionQueries {
  static final String INSERT_SESSION = """
    INSERT INTO story_session (
      session_id, title, created_at, updated_at, last_accessed_at, expires_at, infinite
    ) VALUES (?, ?, ?, ?, ?, ?, ?)
    """;
  static final String INSERT_SESSION_CONFIGURATION = """
    INSERT INTO session_configuration (session_id)
    VALUES (?)
    """;
  static final String INSERT_SESSION_MEMORY = """
    INSERT INTO session_memory (
      session_id, summary_cursor, recent_summary_cursor, canonical_state_cursor
    ) VALUES (?, 0, 0, 0)
    """;
  static final String INSERT_TURN_STATE = """
    INSERT INTO turn_state (session_id, trigger_word, started, round_number)
    VALUES (?, '', FALSE, 0)
    """;
  static final String INSERT_KNOWLEDGE_GRAPH = """
    INSERT INTO knowledge_graph (session_id, schema_version, revision)
    VALUES (?, 1, 0)
    """;
  static final String SELECT_SESSION = """
    SELECT session_id, title, created_at, updated_at, last_accessed_at, expires_at, infinite
    FROM story_session
    WHERE session_id = ?
    """;
  static final String UPDATE_ACCESS = """
    UPDATE story_session
    SET last_accessed_at = ?, expires_at = ?
    WHERE session_id = ?
    """;
  static final String UPDATE_INFINITE = """
    UPDATE story_session
    SET infinite = ?, expires_at = ?
    WHERE session_id = ?
    """;
  static final String DELETE_SESSION = """
    DELETE FROM story_session
    WHERE session_id = ?
    """;
  static final String DELETE_SESSION_FACTS = """
    DELETE FROM knowledge_fact
    WHERE session_id = ?
    """;
  static final String DELETE_EXPIRED_SESSIONS = """
    DELETE FROM story_session
    WHERE infinite = FALSE AND expires_at <= ?
    """;
  static final String DELETE_EXPIRED_SESSION_FACTS = """
    DELETE FROM knowledge_fact
    WHERE session_id IN (
      SELECT session_id
      FROM story_session
      WHERE infinite = FALSE AND expires_at <= ?
    )
    """;

  private SessionQueries() {
  }
}
