package nl.llm.storyteller.db;

final class StoryQueries {
  static final String DELETE_MESSAGES = "DELETE FROM story_message WHERE session_id = ?";
  static final String DELETE_MESSAGES_FROM = """
    DELETE FROM story_message
    WHERE session_id = ? AND message_index >= ?
    """;
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
  static final String SELECT_PAST_EXCHANGES = """
    SELECT user_message.message_index,
           user_message.content AS prompt,
           assistant_message.content AS response
    FROM story_message user_message
    JOIN story_message assistant_message
      ON assistant_message.session_id = user_message.session_id
     AND assistant_message.message_index = user_message.message_index + 1
     AND assistant_message.message_role = 'assistant'
    WHERE user_message.session_id = ?
      AND user_message.message_role = 'user'
      AND user_message.message_index IN (?, ?, ?)
    ORDER BY user_message.message_index
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
