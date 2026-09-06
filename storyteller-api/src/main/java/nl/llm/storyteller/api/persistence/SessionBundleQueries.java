package nl.llm.storyteller.api.persistence;

final class SessionBundleQueries {
  static final String SELECT_MESSAGES = """
    SELECT message_role, content
    FROM story_message
    WHERE session_id = ?
    ORDER BY message_index
    """;
  static final String SELECT_MEMORY = """
    SELECT summary_content, recent_summary_content, canonical_state_content,
           summary_cursor, recent_summary_cursor, canonical_state_cursor
    FROM session_memory
    WHERE session_id = ?
    """;
  static final String SELECT_GRAPH = """
    SELECT schema_version, revision
    FROM knowledge_graph
    WHERE session_id = ?
    """;
  static final String SELECT_TURN_STATE = """
    SELECT trigger_word, started, round_number
    FROM turn_state
    WHERE session_id = ?
    """;
  static final String SELECT_PROTAGONISTS = """
    SELECT protagonist_name, turns_this_round
    FROM turn_protagonist
    WHERE session_id = ?
    ORDER BY protagonist_index
    """;
  static final String SELECT_ENTITIES = """
    SELECT entity_id, entity_type, entity_name, entity_source
    FROM knowledge_entity
    WHERE session_id = ?
    ORDER BY entity_id
    """;
  static final String SELECT_ALIASES = """
    SELECT entity_id, alias_name
    FROM knowledge_entity_alias
    WHERE session_id = ?
    ORDER BY entity_id, alias_index
    """;
  static final String SELECT_FACTS = """
    SELECT fact_id, subject_entity_id, predicate_id, object_entity_id, polarity,
           fact_status, fact_source, source_turn, is_hard
    FROM knowledge_fact
    WHERE session_id = ?
    ORDER BY fact_id
    """;
  static final String INSERT_MEMORY = """
    INSERT INTO session_memory (
      session_id, summary_content, recent_summary_content, canonical_state_content,
      summary_cursor, recent_summary_cursor, canonical_state_cursor
    ) VALUES (?, ?, ?, ?, ?, ?, ?)
    """;
  static final String INSERT_TURN_STATE = """
    INSERT INTO turn_state (session_id, trigger_word, started, round_number)
    VALUES (?, ?, ?, ?)
    """;
  static final String INSERT_PROTAGONIST = """
    INSERT INTO turn_protagonist (
      session_id, protagonist_index, protagonist_name, turns_this_round
    ) VALUES (?, ?, ?, ?)
    """;
  static final String INSERT_GRAPH = """
    INSERT INTO knowledge_graph (session_id, schema_version, revision)
    VALUES (?, ?, ?)
    """;
  static final String INSERT_ENTITY = """
    INSERT INTO knowledge_entity (
      session_id, entity_id, entity_type, entity_name, entity_source
    ) VALUES (?, ?, ?, ?, ?)
    """;
  static final String INSERT_ALIAS = """
    INSERT INTO knowledge_entity_alias (session_id, entity_id, alias_index, alias_name)
    VALUES (?, ?, ?, ?)
    """;
  static final String INSERT_FACT = """
    INSERT INTO knowledge_fact (
      session_id, fact_id, subject_entity_id, predicate_id, object_entity_id,
      polarity, fact_status, fact_source, source_turn, is_hard
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

  private SessionBundleQueries() {
  }
}
