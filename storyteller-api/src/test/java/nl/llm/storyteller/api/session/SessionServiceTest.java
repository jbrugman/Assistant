package nl.llm.storyteller.api.session;

import nl.llm.storyteller.api.persistence.SessionRecord;
import nl.llm.storyteller.api.persistence.SessionRepository;
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

class SessionServiceTest {
  private static final Instant NOW = Instant.parse("2026-09-05T10:15:30Z");

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
      NOW.plusSeconds(60)
    );
    repository.create(stored);
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
      NOW
    );
    repository.create(expired);
    SessionService service = service(repository);

    Optional<SessionRecord> result = service.findActive(expired.sessionId());

    assertFalse(result.isPresent());
    assertFalse(repository.findById(expired.sessionId()).isPresent());
  }

  private SessionService service(SessionRepository repository) {
    return new SessionService(
      repository,
      Clock.fixed(NOW, ZoneOffset.UTC),
      Duration.ofHours(1),
      () -> "generated-session-id"
    );
  }

  private static final class InMemorySessionRepository implements SessionRepository {
    private final Map<String, SessionRecord> sessions = new LinkedHashMap<>();

    @Override
    public void create(SessionRecord session) {
      sessions.put(session.sessionId(), session);
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
        expiresAt
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
      sessions.values().removeIf(session -> !session.expiresAt().isAfter(expiredBefore));
      return originalSize - sessions.size();
    }
  }
}
