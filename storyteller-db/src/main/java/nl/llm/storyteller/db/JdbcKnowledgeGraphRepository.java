package nl.llm.storyteller.db;

import nl.llm.storyteller.core.graph.KnowledgeGraphSnapshot;
import nl.llm.storyteller.core.graph.KnowledgeGraphValidator;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphRepository;

import java.util.function.UnaryOperator;

public final class JdbcKnowledgeGraphRepository implements KnowledgeGraphRepository {
  private final String sessionId;
  private final JdbcSessionBundleRepository repository;
  private final KnowledgeGraphValidator validator;

  public JdbcKnowledgeGraphRepository(Database database, String sessionId, KnowledgeGraphValidator validator) {
    this.sessionId = sessionId;
    this.repository = new JdbcSessionBundleRepository(database);
    this.validator = validator;
  }

  @Override
  public synchronized KnowledgeGraphDocument load() {
    KnowledgeGraphDocument document = repository.loadKnowledgeGraph(sessionId);
    validator.validate(document);
    return document;
  }

  @Override
  public synchronized KnowledgeGraphSnapshot loadSnapshot() {
    return KnowledgeGraphSnapshot.from(load(), validator);
  }

  @Override
  public synchronized void save(KnowledgeGraphDocument document) {
    validator.validate(document);
    repository.saveKnowledgeGraph(sessionId, document);
  }

  @Override
  public synchronized KnowledgeGraphDocument update(UnaryOperator<KnowledgeGraphDocument> update) {
    KnowledgeGraphDocument updated = update.apply(load());
    save(updated);
    return updated;
  }
}
