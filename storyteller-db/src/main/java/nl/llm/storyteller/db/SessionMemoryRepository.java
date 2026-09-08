package nl.llm.storyteller.db;

public interface SessionMemoryRepository {
  SessionMemory load(String sessionId);

  SessionMemory loadForUpdate(String sessionId);

  boolean updateSummary(String sessionId, SessionMemory expected, String content, int cursor);

  boolean updateRecentSummary(String sessionId, SessionMemory expected, String content, int cursor);

  boolean updateCanonicalState(String sessionId, SessionMemory expected, String content, int cursor);
}
