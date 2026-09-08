package nl.llm.storyteller.db;

import nl.llm.storyteller.core.model.HistoryState;
import nl.llm.storyteller.core.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcApplicationStorageTest {
  private static final String SESSION_ID = "cli-session";

  @TempDir
  Path temporaryDirectory;

  private Database database;

  @BeforeEach
  void createDatabaseSession() {
    database = new Database("jdbc:h2:file:" + temporaryDirectory.resolve("cli-storage"), "sa", "");
    new SchemaInitializer(database).initialize();
    Instant now = Instant.parse("2026-09-08T12:00:00Z");
    new JdbcSessionRepository(database).create(new SessionRecord(
      SESSION_ID, "CLI story", now, now, now, now.plusSeconds(3600), true
    ), SessionPrompts.empty());
  }

  @Test
  @DisplayName("""
    Given a CLI session stored in H2,
    When story turns, cursors, and derived memory are updated,
    Then the JDBC application storage should return the complete current state
    """)
  void shouldPersistCliStoryAndDerivedMemory() {
    JdbcStoryHistory history = new JdbcStoryHistory(database, SESSION_ID);
    JdbcTextMemory summary = new JdbcTextMemory(database, SESSION_ID, JdbcTextMemory.Type.SUMMARY);

    history.appendTurn("First prompt", "First response");
    history.appendTurn("Second prompt", "Second response");
    history.markSummarized(2);
    summary.save("Persisted summary");

    assertEquals(List.of(
      new Message("user", "First prompt"),
      new Message("assistant", "First response"),
      new Message("user", "Second prompt"),
      new Message("assistant", "Second response")
    ), history.load().messages());
    assertEquals(2, history.load().summaryCursor());
    assertEquals("Persisted summary", summary.load());
    assertEquals("Second prompt", history.loadLastTurn().userInput());
  }

  @Test
  @DisplayName("""
    Given imported history ending in an incomplete turn,
    When the last complete user turn is inspected and removed,
    Then JDBC history should preserve the file-backed history semantics
    """)
  void shouldHandleIncompleteImportedTurnsLikeFileHistory() {
    JdbcStoryHistory history = new JdbcStoryHistory(database, SESSION_ID);
    history.save(new HistoryState(List.of(
      new Message("user", "Complete prompt"),
      new Message("assistant", "Complete response"),
      new Message("user", "Incomplete prompt")
    ), 3, 3, 3));

    String removed = history.removeLastTurn();

    assertEquals("Incomplete prompt", removed);
    assertEquals(List.of(
      new Message("user", "Complete prompt"),
      new Message("assistant", "Complete response")
    ), history.load().messages());
    assertEquals(2, history.load().summaryCursor());
  }
}
