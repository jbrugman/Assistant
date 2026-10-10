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
    Given a local application.config with a database encryption key,
    When CLI storage writes a turn and is reopened,
    Then database content should be encrypted and source files should remain unchanged
    """)
  void shouldUseConfiguredKeyAndLeaveSourceFilesUnchanged() throws Exception {
    Path file = temporaryDirectory.resolve("application.config");
    String configuration = "database.encryption.key=" + DatabaseEncryptionTest.KEY + "\nfile.rules=original-rules.txt\n";
    Path rules = temporaryDirectory.resolve("original-rules.txt");
    java.nio.file.Files.writeString(rules, "Original plaintext rules");
    java.nio.file.Files.writeString(file, configuration);
    var config = AppConfigLoader.load(temporaryDirectory, file);
    assertEquals(DatabaseEncryptionTest.KEY, config.databaseEncryptionKey());
    var storage = JdbcApplicationStorageFactory.create(config);
    storage.history().appendTurn("Geheim", "Antwoord");
    assertEquals("Geheim", JdbcApplicationStorageFactory.create(config).history().loadLastTurn().userInput());
    assertEquals(configuration, java.nio.file.Files.readString(file));
    assertEquals("Original plaintext rules", java.nio.file.Files.readString(rules));
    var database = new Database("jdbc:h2:file:" + config.databasePath(), "sa", "",
      DatabaseEncryption.fromBase64(config.databaseEncryptionKey()));
    try (var connection = database.openConnection(); var statement = connection.createStatement();
         var result = statement.executeQuery("SELECT content FROM story_message WHERE message_index = 0")) {
      org.junit.jupiter.api.Assertions.assertTrue(result.next());
      org.junit.jupiter.api.Assertions.assertTrue(result.getString(1).startsWith("stenc:v1:"));
    }
  }

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
