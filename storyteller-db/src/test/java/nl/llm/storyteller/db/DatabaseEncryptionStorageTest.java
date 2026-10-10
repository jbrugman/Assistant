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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseEncryptionStorageTest {
  @TempDir Path directory;
  private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
  private static final String ID = "encrypted-session";

  @Test
  @DisplayName("""
    Given an encrypted H2 database,
    When API and CLI repositories read, update, import, and delete session content,
    Then plaintext behavior and expiration should be preserved while stored content remains encrypted
    """)
  void preservesAllRepositoryOperationsWithEncryptedStorage() throws Exception {
    Database database = database(true);
    new SchemaInitializer(database).initialize();
    exerciseRepositories(database);
  }

  static void exerciseRepositories(Database database) throws Exception {
    var sessions = new JdbcSessionRepository(database);
    var bundles = new JdbcSessionBundleRepository(database);
    var stories = new JdbcStoryRepository(database);
    var memories = new JdbcSessionMemoryRepository(database);
    var original = bundle();
    var session = session(ID, false);
    bundles.create(session, original);
    assertEquals(session, sessions.findById(ID).orElseThrow());
    assertEquals(original, bundles.load(ID));
    assertEncryptedValues(database);
    assertEquals(original.history().messages(), stories.loadRecentMessages(ID, 10));
    assertEquals("Verhaal 🦉", stories.loadMessagesBefore(ID, 2, 10).getLast().content());
    assertEquals("Begin", stories.loadPastExchanges(ID, List.of(0)).getFirst().prompt());
    assertEquals(image(), stories.loadImage(ID, 0).orElseThrow());

    String large = "🦉 café ".repeat(150_000);
    var turn = stories.appendTurn(ID, large, "Antwoord", image(), NOW);
    assertEquals(large, stories.loadRecentMessages(ID, 2).getFirst().content());
    assertEquals(image(), stories.loadImage(ID, turn.userMessageIndex()).orElseThrow());
    assertTrue(stories.updateAssistantMessage(ID, turn.assistantMessageIndex(), "Bewerkt", NOW));
    assertEquals("Bewerkt", stories.loadPastExchanges(ID, List.of(turn.userMessageIndex())).getFirst().response());

    var expected = memories.loadForUpdate(ID);
    assertEquals(4, expected.messages().size());
    assertTrue(memories.updateSummary(ID, expected, "Nieuw", 2));
    assertFalse(memories.updateSummary(ID, expected, "Verouderd op dezelfde cursor", 2));
    expected = memories.load(ID);
    assertTrue(memories.updateRecentSummary(ID, expected, "Recent nieuw", 4));
    assertTrue(memories.updateCanonicalState(ID, memories.load(ID), "Staat nieuw", 4));
    assertTrue(stories.undoLastTurn(ID, NOW));
    assertEquals(2, memories.load(ID).canonicalStateCursor());
    assertTrue(memories.resetLongTermMemory(ID));
    assertTrue(memories.resetMidTermMemory(ID));
    assertTrue(memories.resetCanonicalState(ID));
    assertEquals("", memories.load(ID).summary());
    assertTrue(memories.updateSummary(ID, memories.load(ID), "Na reset", 2));

    var prompts = new JdbcSessionPromptRepository(database);
    var changedPrompts = new SessionPrompts("Nieuw systeem", "Nieuwe personages", "Nieuwe regels");
    prompts.save(ID, changedPrompts);
    assertEquals(changedPrompts, prompts.load(ID));
    try (var connection = database.openConnection(); var statement = connection.prepareStatement(
      "DELETE FROM session_prompt_override WHERE session_id = ?")) {
      statement.setString(1, ID);
      statement.executeUpdate();
    }
    sessions.create(session("second-session", true), original.prompts());
    prompts.initializeMissing(changedPrompts);
    assertEquals(changedPrompts, prompts.load(ID));
    assertEquals(original.prompts(), prompts.load("second-session"));
    var settings = new SessionSettings(original.prompts(), original.knowledgeGraph(), 0.7);
    var settingsRepository = new JdbcSessionSettingsRepository(database);
    settingsRepository.save(ID, settings);
    assertEquals(settings, settingsRepository.load(ID));
    assertEquals(original.knowledgeGraph(), bundles.loadKnowledgeGraph(ID));
    var turns = new JdbcTurnStateRepository(database, ID);
    turns.save(original.turnState());
    assertEquals(original.turnState(), turns.load());

    var history = new JdbcStoryHistory(database, ID);
    history.save(new HistoryState(List.of(new Message("user", "CLI"), new Message("assistant", "CLI antwoord")), 2, 2, 2));
    assertEquals("CLI", history.loadLastTurn().userInput());
    for (var type : JdbcTextMemory.Type.values()) {
      var memory = new JdbcTextMemory(database, ID, type);
      memory.save("CLI geheugen");
      assertEquals("CLI geheugen", memory.load());
    }
    assertEquals("CLI", history.removeLastTurn());
    assertTrue(history.load().messages().isEmpty());
    assertTrue(sessions.refreshAccess(ID, NOW.plusSeconds(1), NOW.plusSeconds(3601)));
    assertTrue(sessions.setInfinite(ID, false, NOW));
    assertEquals(1, sessions.deleteExpired(NOW.plusSeconds(1)));
    assertTrue(sessions.findById(ID).isEmpty());
    assertTrue(sessions.findById("second-session").isPresent());
    assertEquals(original.prompts(), bundles.load("second-session").prompts());
    try (var connection = database.openConnection(); var statement = connection.createStatement();
         var result = statement.executeQuery("SELECT COUNT(*) FROM story_message WHERE session_id = 'encrypted-session'")) {
      result.next();
      assertEquals(0, result.getInt(1));
    }
  }

  @Test
  @DisplayName("""
    Given a legacy database with existing session content,
    When encryption is enabled and the application restarts,
    Then content should migrate once and missing or incorrect keys should be rejected
    """)
  void migratesLegacyRowsOnceAndRequiresTheOriginalKeyOnRestart() throws Exception {
    Database plain = database(false);
    new SchemaInitializer(plain).initialize();
    var session = session(ID, false);
    var original = bundle();
    new JdbcSessionBundleRepository(plain).create(session, original);
    Database encrypted = database(true);
    new SchemaInitializer(encrypted).initialize();
    assertEquals(session, new JdbcSessionRepository(encrypted).findById(ID).orElseThrow());
    assertEquals(original, new JdbcSessionBundleRepository(encrypted).load(ID));
    assertEncryptedValues(encrypted);
    String before = raw(encrypted, "story_message", "content");
    new SchemaInitializer(database(true)).initialize();
    assertEquals(before, raw(encrypted, "story_message", "content"));
    assertThrows(IllegalStateException.class, () -> new SchemaInitializer(plain).initialize());
    byte[] wrongKey = new byte[32];
    wrongKey[0] = 1;
    Database wrong = new Database(plain.url(), "sa", "", DatabaseEncryption.fromBase64(Base64.getEncoder().encodeToString(wrongKey)));
    assertThrows(DatabaseException.class, () -> new SchemaInitializer(wrong).initialize());
    assertEquals(original, new JdbcSessionBundleRepository(encrypted).load(ID));
  }

  @Test
  @DisplayName("""
    Given a legacy database and an intentionally failing migration,
    When encryption is enabled and retried after the failure is removed,
    Then the failed migration should roll back and the retry should preserve all content
    """)
  void rollsBackFailedMigrationAndCanRetryWithoutDoubleEncryption() throws Exception {
    Database plain = database(false);
    new SchemaInitializer(plain).initialize();
    var original = bundle();
    new JdbcSessionBundleRepository(plain).create(session(ID, false), original);
    try (var connection = plain.openConnection(); var statement = connection.createStatement()) {
      statement.execute("CREATE TRIGGER reject_encryption BEFORE UPDATE ON story_message FOR EACH ROW CALL "
        + "'nl.llm.storyteller.db.DatabaseEncryptionStorageTest$RejectUpdate'");
    }
    assertThrows(DatabaseException.class, () -> new SchemaInitializer(database(true)).initialize());
    assertEquals(original, new JdbcSessionBundleRepository(plain).load(ID));
    try (var connection = plain.openConnection(); var statement = connection.createStatement()) {
      statement.execute("DROP TRIGGER reject_encryption");
    }
    Database encrypted = database(true);
    new SchemaInitializer(encrypted).initialize();
    assertEquals(original, new JdbcSessionBundleRepository(encrypted).load(ID));
  }

  public static final class RejectUpdate implements org.h2.api.Trigger {
    @Override
    public void fire(java.sql.Connection connection, Object[] oldRow, Object[] newRow) throws java.sql.SQLException {
      throw new java.sql.SQLException("Intentional migration failure");
    }
  }

  @Test
  @DisplayName("""
    Given encrypted story content whose stored ciphertext has been replaced,
    When the story repository loads the messages,
    Then it should reject the damaged value instead of returning it as plaintext
    """)
  void rejectsDamagedCiphertextInsteadOfReturningItAsPlaintext() throws Exception {
    Database database = database(true);
    new SchemaInitializer(database).initialize();
    new JdbcSessionBundleRepository(database).create(session(ID, false), bundle());
    try (var connection = database.openConnection(); var statement = connection.createStatement()) {
      statement.executeUpdate("UPDATE story_message SET content = 'tampered' WHERE message_index = 0");
    }
    assertThrows(DatabaseException.class, () -> new JdbcStoryRepository(database).loadRecentMessages(ID, 10));
  }

  static void assertEncryptedValues(Database database) throws Exception {
    for (String entry : List.of("story_session.title", "session_prompt_override.override_content",
      "story_message.content", "session_memory.summary_content", "session_memory.recent_summary_content",
      "session_memory.canonical_state_content", "turn_state.trigger_word", "turn_protagonist.protagonist_name",
      "knowledge_entity.entity_name", "knowledge_entity_alias.alias_name")) {
      String[] parts = entry.split("\\.");
      assertTrue(raw(database, parts[0], parts[1]).startsWith("stenc:v1:"), entry);
    }
    try (var connection = database.openConnection(); var statement = connection.createStatement();
         var result = statement.executeQuery("SELECT image_content FROM story_message WHERE message_index = 0")) {
      result.next();
      assertFalse(java.util.Arrays.equals(image().content(), result.getBytes(1)));
    }
  }

  private static String raw(Database database, String table, String column) throws Exception {
    try (var connection = database.openConnection(); var statement = connection.createStatement();
         var result = statement.executeQuery("SELECT " + column + " FROM " + table + " WHERE session_id = 'encrypted-session'")) {
      assertTrue(result.next());
      return result.getString(1);
    }
  }

  private Database database(boolean encrypted) {
    return new Database("jdbc:h2:file:" + directory.resolve("encrypted"), "sa", "",
      DatabaseEncryption.fromBase64(encrypted ? DatabaseEncryptionTest.KEY : null));
  }

  private static SessionRecord session(String id, boolean infinite) {
    return new SessionRecord(id, "Verhaal 🦉", NOW, NOW, NOW, NOW.plusSeconds(3600), infinite);
  }

  private static StoryImage image() {
    return new StoryImage("image/png", Base64.getDecoder().decode(
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII="));
  }

  private static SessionBundle bundle() {
    return new SessionBundle(
      new HistoryState(List.of(Message.withImage("user", "Begin", image().dataUrl()), new Message("assistant", "Verhaal 🦉")), 2, 2, 2),
      "Samenvatting", "Recent", "Huidige staat",
      new TurnState("verder", true, 2, List.of("Alice"), Map.of("Alice", 1)),
      new KnowledgeGraphDocument(1, 0,
        Map.of("alice", new Entity(EntityType.CHARACTER, "Alice", List.of("Al"), FactSource.MANUAL),
          "paris", new Entity(EntityType.LOCATION, "Parijs", List.of("Paris"), FactSource.MANUAL)),
        List.of(new Fact("alice-lives-paris", new EntityId("alice"), new PredicateId("LIVES"), new EntityId("paris"),
          Polarity.POSITIVE, FactStatus.ACTIVE, FactSource.MANUAL, 1, true))),
      new SessionPrompts("Systeem", "Vaste personages", "Regels"));
  }
}
