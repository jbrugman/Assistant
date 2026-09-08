package nl.llm.storyteller.db;

import nl.llm.storyteller.core.config.AppConfigLoader;
import nl.llm.storyteller.core.service.StoryPrompts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static nl.llm.storyteller.db.JdbcApplicationStorageFactory.CLI_SESSION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcApplicationStorageFactoryTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  @DisplayName("""
    Given story prompts persisted for the fixed CLI session,
    When CLI application storage is created again,
    Then the persisted prompts should be exposed as the active prompt source
    """)
  void shouldUsePersistedCliSessionPrompts() {
    var config = AppConfigLoader.load(temporaryDirectory, null);
    JdbcApplicationStorageFactory.create(config);
    var database = new Database("jdbc:h2:file:" + config.databasePath(), "sa", "");
    var expected = new SessionPrompts("DATABASE SYSTEM", "DATABASE PROTAGONISTS", "DATABASE RULES");
    new JdbcSessionPromptRepository(database).save(CLI_SESSION_ID, expected);

    StoryPrompts actual = JdbcApplicationStorageFactory.create(config).prompts().load();

    assertEquals(expected.systemPrompt(), actual.systemPrompt());
    assertEquals(expected.fixedProtagonists(), actual.fixedProtagonists());
    assertEquals(expected.rules(), actual.rules());
  }

  @Test
  @DisplayName("Given an existing API session, when CLI storage is created for its ID, then that session is loaded")
  void shouldLoadRequestedSession() {
    var config = AppConfigLoader.load(temporaryDirectory, null);
    JdbcApplicationStorageFactory.create(config);
    var database = new Database("jdbc:h2:file:" + config.databasePath(), "sa", "");
    String sessionId = "05dbf813-8f2b-45f8-abad-93efa01f1199";
    var expected = new SessionPrompts("API SYSTEM", "API PROTAGONISTS", "API RULES");
    var now = java.time.Instant.now();
    new JdbcSessionRepository(database).create(
      new SessionRecord(sessionId, "API story", now, now, now, now.plusSeconds(3600), true),
      expected
    );

    StoryPrompts actual = JdbcApplicationStorageFactory.create(config, sessionId).prompts().load();

    assertEquals(expected.systemPrompt(), actual.systemPrompt());
    assertEquals(expected.fixedProtagonists(), actual.fixedProtagonists());
    assertEquals(expected.rules(), actual.rules());
  }

  @Test
  @DisplayName("Given an unknown session ID, when CLI storage is created, then it should reject the session")
  void shouldRejectUnknownRequestedSession() {
    var config = AppConfigLoader.load(temporaryDirectory, null);

    assertThrows(
      IllegalArgumentException.class,
      () -> JdbcApplicationStorageFactory.create(config, "05dbf813-8f2b-45f8-abad-93efa01f1199")
    );
  }
}
