package nl.llm.storyteller.db;

import java.util.Map;

public interface SessionSettingsRepository {
  SessionSettings load(String sessionId);

  void save(String sessionId, SessionSettings settings);

  void patchConfiguration(String sessionId, Map<String, String> prompts, boolean updateTemperature, Double temperature);

  void setTemperature(String sessionId, Double temperature);
}
