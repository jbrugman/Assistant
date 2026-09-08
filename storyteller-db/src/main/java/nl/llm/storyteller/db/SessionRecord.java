package nl.llm.storyteller.db;

import java.time.Instant;

public record SessionRecord(
  String sessionId,
  String title,
  Instant createdAt,
  Instant updatedAt,
  Instant lastAccessedAt,
  Instant expiresAt,
  boolean infinite
) { }
