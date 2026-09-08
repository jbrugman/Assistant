package nl.llm.storyteller.db;

public interface SessionPromptRepository {
  SessionPrompts load(String sessionId);

  void save(String sessionId, SessionPrompts prompts);
}
