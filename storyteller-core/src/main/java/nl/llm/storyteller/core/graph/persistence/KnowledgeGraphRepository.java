package nl.llm.storyteller.core.graph.persistence;

import nl.llm.storyteller.core.graph.KnowledgeGraphSnapshot;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;

import java.util.function.UnaryOperator;

public interface KnowledgeGraphRepository {
  KnowledgeGraphDocument load();

  KnowledgeGraphSnapshot loadSnapshot();

  void save(KnowledgeGraphDocument document);

  KnowledgeGraphDocument update(UnaryOperator<KnowledgeGraphDocument> update);
}
