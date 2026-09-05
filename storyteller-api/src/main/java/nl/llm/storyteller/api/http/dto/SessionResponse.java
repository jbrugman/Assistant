package nl.llm.storyteller.api.http.dto;

import nl.llm.storyteller.api.persistence.SessionRecord;

public record SessionResponse(
  String sessionId,
  String title,
  String createdAt,
  String updatedAt,
  String lastAccessedAt,
  String expiresAt
) {
  public static SessionResponse from(SessionRecord session) {
    return new SessionResponse(
      session.sessionId(),
      session.title(),
      session.createdAt().toString(),
      session.updatedAt().toString(),
      session.lastAccessedAt().toString(),
      session.expiresAt().toString()
    );
  }
}
