package nl.llm.storyteller.db;

public interface SessionSettingsRepository {
  SessionSettings load(String sessionId);

  void save(String sessionId, SessionSettings settings);

  void setTemperature(String sessionId, Double temperature);
}
