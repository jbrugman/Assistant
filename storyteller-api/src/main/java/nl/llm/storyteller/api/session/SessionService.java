package nl.llm.storyteller.api.session;

import nl.llm.storyteller.db.SessionPrompts;
import nl.llm.storyteller.db.SessionRecord;
import nl.llm.storyteller.db.SessionRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static nl.llm.storyteller.api.input.TextInputNormalizer.optionalSingleLine;

public final class SessionService {
  private static final int MAX_TITLE_LENGTH = 255;

  private final SessionRepository repository;
  private final Clock clock;
  private final Duration inactivityTimeout;
  private final Supplier<String> idSupplier;
  private final SessionPrompts defaultPrompts;

  public SessionService(
    SessionRepository repository,
    Duration inactivityTimeout,
    SessionPrompts defaultPrompts
  ) {
    this(
      repository,
      Clock.systemUTC(),
      inactivityTimeout,
      () -> UUID.randomUUID().toString(),
      defaultPrompts
    );
  }

  SessionService(
    SessionRepository repository,
    Clock clock,
    Duration inactivityTimeout,
    Supplier<String> idSupplier,
    SessionPrompts defaultPrompts
  ) {
    this.repository = repository;
    this.clock = clock;
    this.inactivityTimeout = inactivityTimeout;
    this.idSupplier = idSupplier;
    this.defaultPrompts = defaultPrompts;
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
      now.plus(inactivityTimeout),
      false
    );
    repository.create(session, defaultPrompts);
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
    if (!session.infinite() && !session.expiresAt().isAfter(now)) {
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
      refreshedExpiry,
      session.infinite()
    ));
  }

  public Optional<SessionRecord> toggleInfinite(String sessionId) {
    Optional<SessionRecord> active = findActive(sessionId);
    if (active.isEmpty()) {
      return Optional.empty();
    }
    SessionRecord session = active.get();
    boolean infinite = !session.infinite();
    Instant expiresAt = clock.instant().plus(inactivityTimeout);
    if (!repository.setInfinite(sessionId, infinite, expiresAt)) {
      return Optional.empty();
    }
    return Optional.of(new SessionRecord(
      session.sessionId(),
      session.title(),
      session.createdAt(),
      session.updatedAt(),
      session.lastAccessedAt(),
      expiresAt,
      infinite
    ));
  }

  public Optional<SessionRecord> resumeInfinite(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return Optional.empty();
    }
    Optional<SessionRecord> stored = repository.findById(sessionId.trim());
    if (stored.isEmpty() || !stored.get().infinite()) {
      return Optional.empty();
    }
    return findActive(stored.get().sessionId());
  }

  public void deleteExpired() {
    repository.deleteExpired(clock.instant());
  }

  public void delete(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return;
    }
    repository.delete(sessionId);
  }

  private String normalizeTitle(String title) {
    return optionalSingleLine(title, "Session title", MAX_TITLE_LENGTH);
  }
}
