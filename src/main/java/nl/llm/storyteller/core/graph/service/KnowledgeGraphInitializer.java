package nl.llm.storyteller.core.graph.service;

import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphStore;

import java.util.List;
import java.util.Map;

public final class KnowledgeGraphInitializer {
  private final KnowledgeGraphStore store;
  private final ReadOnlyKnowledgeGraphService graphService;

  public KnowledgeGraphInitializer(KnowledgeGraphStore store, ReadOnlyKnowledgeGraphService graphService) {
    this.store = store;
    this.graphService = graphService;
  }

  public KnowledgeGraphDocument generateEmpty() {
    KnowledgeGraphDocument document = new KnowledgeGraphDocument(
      KnowledgeGraphDocument.CURRENT_SCHEMA_VERSION,
      graphService.current().revision() + 1,
      Map.of(),
      List.of()
    );
    store.save(document);
    graphService.publish(store.loadSnapshot());
    return document;
  }
}
