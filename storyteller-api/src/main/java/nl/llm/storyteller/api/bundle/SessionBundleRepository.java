package nl.llm.storyteller.api.bundle;

import nl.llm.storyteller.api.persistence.SessionRecord;

public interface SessionBundleRepository {
  SessionBundle load(String sessionId);

  void create(SessionRecord session, SessionBundle bundle);
}
