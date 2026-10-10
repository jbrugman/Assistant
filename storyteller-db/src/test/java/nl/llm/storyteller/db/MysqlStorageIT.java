package nl.llm.storyteller.db;

import nl.llm.storyteller.db.bundle.SessionBundle;
import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.EntityType;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.model.PredicateId;
import nl.llm.storyteller.core.model.HistoryState;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.model.TurnState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import nl.llm.storyteller.core.config.MysqlConfig;
import nl.llm.storyteller.core.config.AppConfigLoader;
import java.util.Properties;
import java.nio.file.Files;
import java.util.UUID;

@EnabledIfSystemProperty(named = "mysql.integration", matches = "true")
class MysqlStorageIT {
  @TempDir Path temporaryDirectory;

  @Test
  void shouldRoundTripSessionContentAndSupportStoryOperations() throws Exception {
    Path configFile = Path.of(System.getProperty("mysql.config")).toAbsolutePath();
    Properties properties = new Properties();
    try (var reader = Files.newBufferedReader(configFile)) {
      properties.load(reader);
    }
    MysqlConfig mysql = MysqlConfig.from(properties);
    assertTrue(mysql.enabled());
    Database database = new Database(mysql.url(), mysql.username(), mysql.password());
    SchemaInitializer initializer = new SchemaInitializer(database);
    initializer.initialize();
    initializer.initialize();
    JdbcSessionRepository sessions = new JdbcSessionRepository(database);
    JdbcSessionBundleRepository bundles = new JdbcSessionBundleRepository(database);
    JdbcStoryRepository stories = new JdbcStoryRepository(database);
    JdbcSessionMemoryRepository memories = new JdbcSessionMemoryRepository(database);
    String id = UUID.randomUUID().toString();
    Instant now = Instant.parse("2026-10-09T12:00:00.123456Z");
    SessionRecord session = new SessionRecord(id, "MySQL verification", now, now, now, now.plusSeconds(3600), false);
    SessionBundle original = bundle();
    try {
      bundles.create(session, original);
      assertEquals(session, sessions.findById(id).orElseThrow());
      assertEquals(original, bundles.load(id));
      try (var connection = database.openConnection(); var statement = connection.prepareStatement(
        "DELETE FROM session_prompt_override WHERE session_id = ?")) {
        statement.setString(1, id);
        statement.executeUpdate();
      }
      var prompts = new JdbcSessionPromptRepository(database);
      prompts.initializeMissing(original.prompts());
      assertEquals(original.prompts(), prompts.load(id));
      assertTrue(sessions.refreshAccess(id, now.plusSeconds(1), now.plusSeconds(3601)));
      var cliConfig = AppConfigLoader.load(temporaryDirectory, configFile);
      var storage = JdbcApplicationStorageFactory.create(cliConfig, id);
      assertEquals(original.history(), storage.history().load());
      byte[] image = new byte[100_000];
      byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
      System.arraycopy(signature, 0, image, 0, signature.length);
      String largeContent = "A🦉é ".repeat(30_000);
      var turn = stories.appendTurn(id, largeContent, "New response", new StoryImage("image/png", image), now);
      assertEquals(largeContent, stories.loadRecentMessages(id, 2).getFirst().content());
      assertEquals(2, stories.loadMessagesBefore(id, turn.userMessageIndex(), 2).size());
      assertEquals(new StoryImage("image/png", image), stories.loadImage(id, turn.userMessageIndex()).orElseThrow());
      assertEquals(2, stories.loadPastExchanges(id, List.of(0, 2)).size());
      var expected = memories.load(id);
      assertTrue(memories.updateSummary(id, expected, "Updated summary", 4));
      assertFalse(memories.updateSummary(id, expected, "Stale summary", 4));
      assertTrue(stories.updateAssistantMessage(id, turn.assistantMessageIndex(), "Edited", now));
      assertTrue(stories.undoLastTurn(id, now));
      assertEquals(2, stories.loadRecentMessages(id, 10).size());
      assertEquals(2, memories.load(id).summaryCursor());
      assertTrue(memories.resetLongTermMemory(id));
      SessionSettings settings = new SessionSettings(original.prompts(), original.knowledgeGraph(), 0.7);
      var settingsRepository = new JdbcSessionSettingsRepository(database);
      settingsRepository.save(id, settings);
      assertEquals(settings, settingsRepository.load(id));
      assertTrue(sessions.setInfinite(id, true, now.plusSeconds(3600)));
      sessions.delete(id);
      assertTrue(sessions.findById(id).isPresent());
    } finally {
      sessions.setInfinite(id, false, now);
      sessions.delete(id);
    }
    assertTrue(sessions.findById(id).isEmpty());
  }

  private SessionBundle bundle() {
    var entities = new LinkedHashMap<String, Entity>();
    entities.put("alice", new Entity(EntityType.CHARACTER, "Alice", List.of("Al"), FactSource.MANUAL));
    entities.put("paris", new Entity(EntityType.LOCATION, "Paris", List.of(), FactSource.MANUAL));
    Fact fact = new Fact(
      "alice-lives-paris",
      new EntityId("alice"),
      new PredicateId("LIVES"),
      new EntityId("paris"),
      Polarity.POSITIVE,
      FactStatus.ACTIVE,
      FactSource.MANUAL,
      0,
      true
    );
    return new SessionBundle(
      new HistoryState(
        List.of(new Message("user", "Begin"), new Message("assistant", "Alice wakes.")),
        2,
        2,
        2
      ),
      "Summary",
      "Recent",
      "location: Paris",
      new TurnState("go", true, 2, List.of("Alice"), java.util.Map.of("Alice", 1)),
      new KnowledgeGraphDocument(1, 3, entities, List.of(fact)),
      new SessionPrompts("System", "fixed_protagonists: []", "Rules")
    );
  }
}
