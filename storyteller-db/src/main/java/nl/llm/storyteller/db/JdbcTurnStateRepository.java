package nl.llm.storyteller.db;

import nl.llm.storyteller.core.model.TurnState;
import nl.llm.storyteller.core.service.TurnStateRepository;

public final class JdbcTurnStateRepository implements TurnStateRepository {
  private final String sessionId;
  private final JdbcSessionBundleRepository repository;

  public JdbcTurnStateRepository(Database database, String sessionId) {
    this.sessionId = sessionId;
    this.repository = new JdbcSessionBundleRepository(database);
  }

  @Override
  public TurnState load() {
    return repository.loadTurnState(sessionId);
  }

  @Override
  public void save(TurnState state) {
    repository.saveTurnState(sessionId, state);
  }
}
