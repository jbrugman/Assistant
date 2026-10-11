package nl.llm.storyteller.db;

import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcSessionSettingsRepositoryTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  @DisplayName("Configuration patches roll back all prompt changes when a later update fails")
  void shouldRollBackConfigurationPatchOnFailure() {
    Database database = new Database("jdbc:h2:file:" + temporaryDirectory.resolve("patch"), "sa", "");
    new SchemaInitializer(database).initialize();
    Instant now = Instant.parse("2026-09-08T20:00:00Z");
    new JdbcSessionRepository(database).create(
      new SessionRecord("session-id", "Story", now, now, now, now.plusSeconds(3600), false),
      new SessionPrompts("System", "Fixed", "Rules")
    );
    JdbcSessionSettingsRepository repository = new JdbcSessionSettingsRepository(database);
    SessionSettings original = repository.load("session-id");
    var patch = new LinkedHashMap<String, String>();
    patch.put(SessionPrompts.SYSTEM_PROMPT_NAME, "Changed");
    patch.put("missing-prompt", "Will fail");

    assertThrows(DatabaseException.class, () -> repository.patchConfiguration("session-id", patch, true, 0.2));

    assertEquals(original, repository.load("session-id"));
  }

  @Test
  @DisplayName("""
    Given a database-backed story session,
    When its editable prompts and knowledge graph are saved,
    Then all settings should be loaded from the same session
    """)
  void shouldSaveAndLoadCompleteSessionSettings() {
    Database database = new Database(
      "jdbc:h2:file:" + temporaryDirectory.resolve("settings"),
      "sa",
      ""
    );
    new SchemaInitializer(database).initialize();
    Instant now = Instant.parse("2026-09-08T20:00:00Z");
    SessionPrompts initialPrompts = new SessionPrompts("System", "Fixed", "Rules");
    new JdbcSessionRepository(database).create(
      new SessionRecord("session-id", "Story", now, now, now, now.plusSeconds(3600), false),
      initialPrompts
    );
    JdbcSessionSettingsRepository repository = new JdbcSessionSettingsRepository(database);
    JdbcSessionMemoryRepository memoryRepository = new JdbcSessionMemoryRepository(database);
    SessionMemory originalMemory = memoryRepository.load("session-id");
    memoryRepository.updateCanonicalState("session-id", originalMemory, "Generated canonical state", 0);
    SessionSettings updated = new SessionSettings(
      new SessionPrompts("New system", "New fixed", "New rules"),
      KnowledgeGraphDocument.empty()
    );

    repository.save("session-id", updated);

    assertEquals(updated, repository.load("session-id"));
    assertEquals("Generated canonical state", memoryRepository.load("session-id").canonicalState());
  }
}
