package nl.llm.storyteller.api.persistence;

final class StoryQueries {
  static final String SELECT_RECENT_MESSAGES = """
    SELECT message_index, message_role, content, image_media_type, image_content
    FROM story_message
    WHERE session_id = ?
    ORDER BY message_index DESC
    FETCH FIRST ? ROWS ONLY
    """;
  static final String SELECT_MESSAGES_BEFORE = """
    SELECT message_index, message_role, content, image_media_type, image_content
    FROM story_message
    WHERE session_id = ? AND message_index < ?
    ORDER BY message_index DESC
    FETCH FIRST ? ROWS ONLY
    """;
  static final String SELECT_LAST_MESSAGE_INDEX = """
    SELECT MAX(message_index) AS last_message_index
    FROM story_message
    WHERE session_id = ?
    """;
  static final String INSERT_MESSAGE = """
    INSERT INTO story_message (
      session_id, message_index, message_role, content, image_media_type, image_content
    )
    VALUES (?, ?, ?, ?, ?, ?)
    """;
  static final String SELECT_IMAGE = """
    SELECT image_media_type, image_content
    FROM story_message
    WHERE session_id = ? AND message_index = ?
    """;
  static final String UPDATE_SESSION_AFTER_TURN = """
    UPDATE story_session
    SET updated_at = ?
    WHERE session_id = ?
    """;
  static final String DELETE_MESSAGE = """
    DELETE FROM story_message
    WHERE session_id = ? AND message_index = ?
    """;

  private StoryQueries() {
  }
}
