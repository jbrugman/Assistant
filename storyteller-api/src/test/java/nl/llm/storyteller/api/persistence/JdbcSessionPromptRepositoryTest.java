package nl.llm.storyteller.api.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcSessionPromptRepositoryTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  @DisplayName("""
    Given a session with prompt overrides,
    When all editable prompts are changed,
    Then the new values should be loaded from H2
    """)
  void shouldUpdateAndLoadSessionPrompts() {
    Database database = new Database(
      "jdbc:h2:file:" + temporaryDirectory.resolve("prompts"),
      "sa",
      ""
    );
    new SchemaInitializer(database).initialize();
    JdbcSessionRepository sessionRepository = new JdbcSessionRepository(database);
    JdbcSessionPromptRepository promptRepository = new JdbcSessionPromptRepository(database);
    Instant now = Instant.parse("2026-09-08T08:00:00Z");
    SessionRecord session = new SessionRecord(
      "session-id", "Story", now, now, now, now.plusSeconds(3600), false
    );
    sessionRepository.create(
      session,
      new SessionPrompts("Initial system", "Initial protagonists", "Initial rules")
    );

    SessionPrompts updated = new SessionPrompts(
      "Updated system",
      "Updated protagonists",
      "Updated rules"
    );
    promptRepository.save(session.sessionId(), updated);

    assertEquals(updated, promptRepository.load(session.sessionId()));
  }

  @Test
  @DisplayName("""
    Given an existing session with a missing prompt row and a customized prompt,
    When missing prompts are initialized during startup,
    Then the missing value should receive its default without replacing the customized value
    """)
  void shouldInitializeOnlyMissingSessionPrompts() throws Exception {
    Database database = new Database(
      "jdbc:h2:file:" + temporaryDirectory.resolve("missing-prompts"),
      "sa",
      ""
    );
    new SchemaInitializer(database).initialize();
    JdbcSessionRepository sessionRepository = new JdbcSessionRepository(database);
    JdbcSessionPromptRepository promptRepository = new JdbcSessionPromptRepository(database);
    Instant now = Instant.parse("2026-09-08T08:00:00Z");
    SessionRecord session = new SessionRecord(
      "existing-session", "Story", now, now, now, now.plusSeconds(3600), false
    );
    sessionRepository.create(
      session,
      new SessionPrompts("Customized system", "Existing protagonists", "Existing rules")
    );
    try (var connection = database.openConnection();
         var statement = connection.prepareStatement("""
           DELETE FROM session_prompt_override
           WHERE session_id = ? AND override_name = ?
           """)) {
      statement.setString(1, session.sessionId());
      statement.setString(2, SessionPrompts.RULES_NAME);
      statement.executeUpdate();
    }

    promptRepository.initializeMissing(
      new SessionPrompts("Default system", "Default protagonists", "Default rules")
    );

    SessionPrompts prompts = promptRepository.load(session.sessionId());
    assertEquals("Customized system", prompts.systemPrompt());
    assertEquals("Default rules", prompts.rules());
  }
}
