package nl.llm.storyteller.api.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcSessionRepositoryTest {
  @TempDir
  Path temporaryDirectory;

  private Database database;
  private JdbcSessionRepository repository;

  @BeforeEach
  void setUp() {
    database = new Database("jdbc:h2:file:" + temporaryDirectory.resolve("repository"), "sa", "");
    new SchemaInitializer(database).initialize();
    repository = new JdbcSessionRepository(database);
  }

  @Test
  @DisplayName("""
    Given a new story session,
    When the session is persisted,
    Then it should store the session and initialize all required state rows atomically
    """)
  void shouldCreateSessionWithRequiredState() throws Exception {
    Instant now = Instant.parse("2026-09-05T10:15:30Z");
    SessionRecord session = new SessionRecord(
      "0f5bbac5-bbf5-491c-ab66-e0d8d47f6559",
      "My story",
      now,
      now,
      now,
      now.plusSeconds(3600),
      false
    );

    repository.create(session);

    assertEquals(session, repository.findById(session.sessionId()).orElseThrow());
    assertEquals(1, rowCount("session_configuration", session.sessionId()));
    assertEquals(1, rowCount("session_memory", session.sessionId()));
    assertEquals(1, rowCount("turn_state", session.sessionId()));
    assertEquals(1, rowCount("knowledge_graph", session.sessionId()));
  }

  @Test
  @DisplayName("""
    Given an expired session with dependent state,
    When expired sessions are deleted,
    Then it should remove the session and cascade to its owned state
    """)
  void shouldDeleteExpiredSessionAndOwnedState() throws Exception {
    Instant now = Instant.parse("2026-09-05T10:15:30Z");
    SessionRecord session = new SessionRecord(
      "d741761d-9c04-43f5-b08e-c13d55553af7",
      null,
      now.minusSeconds(7200),
      now.minusSeconds(7200),
      now.minusSeconds(7200),
      now.minusSeconds(3600),
      false
    );
    repository.create(session);

    int deleted = repository.deleteExpired(now);

    assertEquals(1, deleted);
    assertFalse(repository.findById(session.sessionId()).isPresent());
    assertEquals(0, rowCount("session_memory", session.sessionId()));
  }

  @Test
  @DisplayName("""
    Given an active persisted session,
    When its access window is refreshed,
    Then it should update access and expiry without changing the content update timestamp
    """)
  void shouldRefreshOnlyAccessTimestamps() {
    Instant created = Instant.parse("2026-09-05T10:15:30Z");
    SessionRecord session = new SessionRecord(
      "b09562a4-7516-4b50-89bc-065f012e6516",
      "Story",
      created,
      created,
      created,
      created.plusSeconds(3600),
      false
    );
    repository.create(session);
    Instant accessed = created.plusSeconds(600);

    assertTrue(repository.refreshAccess(session.sessionId(), accessed, accessed.plusSeconds(3600)));

    SessionRecord refreshed = repository.findById(session.sessionId()).orElseThrow();
    assertEquals(created, refreshed.updatedAt());
    assertEquals(accessed, refreshed.lastAccessedAt());
    assertEquals(accessed.plusSeconds(3600), refreshed.expiresAt());
  }

  private int rowCount(String table, String sessionId) throws Exception {
    String sql = "SELECT COUNT(*) FROM " + table + " WHERE session_id = ?";
    try (Connection connection = database.openConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getInt(1);
      }
    }
  }
}
