package nl.llm.storyteller.api.session;

import nl.llm.storyteller.api.persistence.SessionRecord;
import nl.llm.storyteller.api.persistence.SessionRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class SessionService {
  private static final int MAX_TITLE_LENGTH = 255;

  private final SessionRepository repository;
  private final Clock clock;
  private final Duration inactivityTimeout;
  private final Supplier<String> idSupplier;

  public SessionService(SessionRepository repository, Duration inactivityTimeout) {
    this(repository, Clock.systemUTC(), inactivityTimeout, () -> UUID.randomUUID().toString());
  }

  SessionService(
    SessionRepository repository,
    Clock clock,
    Duration inactivityTimeout,
    Supplier<String> idSupplier
  ) {
    this.repository = repository;
    this.clock = clock;
    this.inactivityTimeout = inactivityTimeout;
    this.idSupplier = idSupplier;
  }

  public SessionRecord create(String title) {
    String normalizedTitle = normalizeTitle(title);
    Instant now = clock.instant();
    SessionRecord session = new SessionRecord(
      idSupplier.get(),
      normalizedTitle,
      now,
      now,
      now,
      now.plus(inactivityTimeout)
    );
    repository.create(session);
    return session;
  }

  public Optional<SessionRecord> findActive(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return Optional.empty();
    }

    Optional<SessionRecord> stored = repository.findById(sessionId);
    if (stored.isEmpty()) {
      return Optional.empty();
    }

    Instant now = clock.instant();
    SessionRecord session = stored.get();
    if (!session.expiresAt().isAfter(now)) {
      repository.delete(sessionId);
      return Optional.empty();
    }

    Instant refreshedExpiry = now.plus(inactivityTimeout);
    if (!repository.refreshAccess(sessionId, now, refreshedExpiry)) {
      return Optional.empty();
    }
    return Optional.of(new SessionRecord(
      session.sessionId(),
      session.title(),
      session.createdAt(),
      session.updatedAt(),
      now,
      refreshedExpiry
    ));
  }

  public void deleteExpired() {
    repository.deleteExpired(clock.instant());
  }

  private String normalizeTitle(String title) {
    if (title == null || title.isBlank()) {
      return null;
    }
    String normalized = title.trim();
    if (normalized.length() > MAX_TITLE_LENGTH) {
      throw new IllegalArgumentException("Session title must not exceed 255 characters.");
    }
    return normalized;
  }
}
