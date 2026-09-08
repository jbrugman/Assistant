package nl.llm.storyteller.api.persistence;

public interface SessionPromptRepository {
  SessionPrompts load(String sessionId);

  void save(String sessionId, SessionPrompts prompts);
}
