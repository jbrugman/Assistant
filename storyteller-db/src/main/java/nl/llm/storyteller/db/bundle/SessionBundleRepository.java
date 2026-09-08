package nl.llm.storyteller.db.bundle;

import nl.llm.storyteller.db.SessionRecord;

public interface SessionBundleRepository {
  SessionBundle load(String sessionId);

  void create(SessionRecord session, SessionBundle bundle);
}
