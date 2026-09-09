package nl.llm.storyteller.api.session;

import nl.llm.storyteller.db.SessionPrompts;
import nl.llm.storyteller.db.SessionRecord;
import nl.llm.storyteller.db.SessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionServiceTest {
  private static final Instant NOW = Instant.parse("2026-09-05T10:15:30Z");
  private static final SessionPrompts DEFAULT_PROMPTS =
    new SessionPrompts("System", "Fixed protagonists", "Rules");

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   "})
  @DisplayName("""
    Given an absent or blank session title,
    When a session is created,
    Then it should persist the session without a title
    """)
  void shouldNormalizeBlankTitle(String title) {
    InMemorySessionRepository repository = new InMemorySessionRepository();
    SessionService service = service(repository);

    SessionRecord created = service.create(title);

    assertNull(created.title());
    assertEquals(NOW.plusSeconds(3600), created.expiresAt());
    assertEquals(created, repository.findById(created.sessionId()).orElseThrow());
    assertEquals(DEFAULT_PROMPTS, repository.prompts);
  }

  @Test
  @DisplayName("""
    Given an active session,
    When the session is accessed,
    Then it should extend its inactivity window without changing its update timestamp
    """)
  void shouldRefreshActiveSession() {
    InMemorySessionRepository repository = new InMemorySessionRepository();
    Instant createdAt = NOW.minusSeconds(600);
    SessionRecord stored = new SessionRecord(
      "session-id",
      "Story",
      createdAt,
      createdAt,
      createdAt,
      NOW.plusSeconds(60),
      false
    );
    repository.create(stored, SessionPrompts.empty());
    SessionService service = service(repository);

    SessionRecord result = service.findActive(stored.sessionId()).orElseThrow();

    assertEquals(createdAt, result.updatedAt());
    assertEquals(NOW, result.lastAccessedAt());
    assertEquals(NOW.plusSeconds(3600), result.expiresAt());
  }

  @Test
  @DisplayName("""
    Given a session whose inactivity window has expired,
    When the session is accessed,
    Then it should delete the expired session and return no result
    """)
  void shouldDeleteExpiredSessionOnAccess() {
    InMemorySessionRepository repository = new InMemorySessionRepository();
    SessionRecord expired = new SessionRecord(
      "expired-session",
      null,
      NOW.minusSeconds(7200),
      NOW.minusSeconds(7200),
      NOW.minusSeconds(7200),
      NOW,
      false
    );
    repository.create(expired, SessionPrompts.empty());
    SessionService service = service(repository);

    Optional<SessionRecord> result = service.findActive(expired.sessionId());

    assertFalse(result.isPresent());
    assertFalse(repository.findById(expired.sessionId()).isPresent());
  }

  @Test
  @DisplayName("""
    Given an active session,
    When the session is deleted,
    Then it should no longer be available
    """)
  void shouldDeleteSession() {
    InMemorySessionRepository repository = new InMemorySessionRepository();
    SessionService service = service(repository);
    SessionRecord session = service.create("Story");

    service.delete(session.sessionId());

    assertFalse(repository.findById(session.sessionId()).isPresent());
  }

  @Test
  @DisplayName("An infinite session can never be deleted")
  void shouldNeverDeleteInfiniteSession() {
    InMemorySessionRepository repository = new InMemorySessionRepository();
    SessionService service = service(repository);
    SessionRecord session = service.create("Story");
    service.toggleInfinite(session.sessionId());

    service.delete(session.sessionId());

    assertTrue(repository.findById(session.sessionId()).orElseThrow().infinite());
  }

  @Test
  @DisplayName("""
    Given an active session with inactivity expiration,
    When infinite retention is enabled,
    Then expiration should be disabled until it is toggled off again
    """)
  void shouldToggleInfiniteRetention() {
    InMemorySessionRepository repository = new InMemorySessionRepository();
    SessionService service = service(repository);
    SessionRecord session = service.create("Story");

    SessionRecord infinite = service.toggleInfinite(session.sessionId()).orElseThrow();
    SessionRecord finite = service.toggleInfinite(session.sessionId()).orElseThrow();

    assertTrue(infinite.infinite());
    assertFalse(finite.infinite());
  }

  @Test
  @DisplayName("""
    Given an infinite session,
    When it is resumed using its session id,
    Then it should be returned with a refreshed access time
    """)
  void shouldResumeInfiniteSession() {
    InMemorySessionRepository repository = new InMemorySessionRepository();
    SessionService service = service(repository);
    SessionRecord session = service.create("Story");
    service.toggleInfinite(session.sessionId());

    SessionRecord resumed = service.resumeInfinite("  " + session.sessionId() + "  ").orElseThrow();

    assertTrue(resumed.infinite());
    assertEquals(NOW, resumed.lastAccessedAt());
  }

  @Test
  @DisplayName("""
    Given a session with inactivity expiration,
    When another client tries to resume it by id,
    Then it should not grant access
    """)
  void shouldNotResumeFiniteSession() {
    InMemorySessionRepository repository = new InMemorySessionRepository();
    SessionService service = service(repository);
    SessionRecord session = service.create("Story");

    assertTrue(service.resumeInfinite(session.sessionId()).isEmpty());
  }

  private SessionService service(SessionRepository repository) {
    return new SessionService(
      repository,
      Clock.fixed(NOW, ZoneOffset.UTC),
      Duration.ofHours(1),
      () -> "generated-session-id",
      DEFAULT_PROMPTS
    );
  }

  private static final class InMemorySessionRepository implements SessionRepository {
    private final Map<String, SessionRecord> sessions = new LinkedHashMap<>();
    private SessionPrompts prompts;

    @Override
    public void create(SessionRecord session, SessionPrompts prompts) {
      sessions.put(session.sessionId(), session);
      this.prompts = prompts;
    }

    @Override
    public Optional<SessionRecord> findById(String sessionId) {
      return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public boolean refreshAccess(String sessionId, Instant accessedAt, Instant expiresAt) {
      SessionRecord session = sessions.get(sessionId);
      if (session == null) {
        return false;
      }
      sessions.put(sessionId, new SessionRecord(
        session.sessionId(),
        session.title(),
        session.createdAt(),
        session.updatedAt(),
        accessedAt,
        expiresAt,
        session.infinite()
      ));
      return true;
    }

    @Override
    public boolean setInfinite(String sessionId, boolean infinite, Instant expiresAt) {
      SessionRecord session = sessions.get(sessionId);
      if (session == null) {
        return false;
      }
      sessions.put(sessionId, new SessionRecord(
        session.sessionId(),
        session.title(),
        session.createdAt(),
        session.updatedAt(),
        session.lastAccessedAt(),
        expiresAt,
        infinite
      ));
      return true;
    }

    @Override
    public void delete(String sessionId) {
      sessions.remove(sessionId);
    }

    @Override
    public int deleteExpired(Instant expiredBefore) {
      int originalSize = sessions.size();
      sessions.values().removeIf(session -> !session.infinite() && !session.expiresAt().isAfter(expiredBefore));
      return originalSize - sessions.size();
    }
  }
}
