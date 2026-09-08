package nl.llm.storyteller.api.persistence;

final class SessionPromptQueries {
  static final String SELECT_PROMPTS = """
    SELECT override_name, override_content
    FROM session_prompt_override
    WHERE session_id = ?
    """;
  static final String INSERT_MISSING_PROMPT = """
    INSERT INTO session_prompt_override (session_id, override_name, override_content)
    SELECT session_id, ?, ?
    FROM story_session
    WHERE NOT EXISTS (
      SELECT 1
      FROM session_prompt_override
      WHERE session_prompt_override.session_id = story_session.session_id
        AND session_prompt_override.override_name = ?
    )
    """;
  static final String INSERT_PROMPT = """
    INSERT INTO session_prompt_override (session_id, override_name, override_content)
    VALUES (?, ?, ?)
    """;
  static final String UPDATE_PROMPT = """
    UPDATE session_prompt_override
    SET override_content = ?
    WHERE session_id = ? AND override_name = ?
    """;

  private SessionPromptQueries() {
  }
}
