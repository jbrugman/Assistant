package nl.llm.storyteller.api.persistence;

import java.time.Instant;

public record SessionRecord(
  String sessionId,
  String title,
  Instant createdAt,
  Instant updatedAt,
  Instant lastAccessedAt,
  Instant expiresAt
) { }
