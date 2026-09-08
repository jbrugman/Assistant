package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.TurnState;

public interface TurnStateRepository {
  TurnState load();

  void save(TurnState state);
}
