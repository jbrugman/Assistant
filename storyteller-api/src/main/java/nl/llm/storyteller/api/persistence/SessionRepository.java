package nl.llm.storyteller.api.persistence;

import java.time.Instant;
import java.util.Optional;

public interface SessionRepository {
  void create(SessionRecord session);

  Optional<SessionRecord> findById(String sessionId);

  boolean refreshAccess(String sessionId, Instant accessedAt, Instant expiresAt);

  boolean setInfinite(String sessionId, boolean infinite, Instant expiresAt);

  void delete(String sessionId);

  int deleteExpired(Instant expiredBefore);
}
