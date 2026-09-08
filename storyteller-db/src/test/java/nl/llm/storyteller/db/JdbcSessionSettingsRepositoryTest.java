package nl.llm.storyteller.db;

import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcSessionSettingsRepositoryTest {
  @TempDir
  Path temporaryDirectory;

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
