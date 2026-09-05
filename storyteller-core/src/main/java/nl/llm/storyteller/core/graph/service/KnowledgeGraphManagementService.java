package nl.llm.storyteller.core.graph.service;

import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphStore;

import java.util.LinkedHashMap;

public final class KnowledgeGraphManagementService {
  private final KnowledgeGraphStore store;
  private final ReadOnlyKnowledgeGraphService graphService;

  public KnowledgeGraphManagementService(
    KnowledgeGraphStore store,
    ReadOnlyKnowledgeGraphService graphService
  ) {
    this.store = store;
    this.graphService = graphService;
  }

  public ResetResult resetTurnBasedItems() {
    int[] removed = new int[2];
    KnowledgeGraphDocument updated = store.update(current -> {
      var entities = new LinkedHashMap<>(current.entities());
      entities.entrySet().removeIf(entry -> entry.getValue().source() == FactSource.TURNBASED);
      var facts = current.facts().stream()
        .filter(fact -> fact.source() != FactSource.TURNBASED)
        .toList();
      removed[0] = current.entities().size() - entities.size();
      removed[1] = current.facts().size() - facts.size();
      if (entities.size() == current.entities().size() && facts.size() == current.facts().size()) {
        return current;
      }
      return new KnowledgeGraphDocument(
        KnowledgeGraphDocument.CURRENT_SCHEMA_VERSION,
        current.revision() + 1,
        entities,
        facts
      );
    });
    graphService.publish(store.loadSnapshot());
    return new ResetResult(
      removed[0],
      removed[1],
      updated.revision()
    );
  }

  public record ResetResult(int entitiesRemoved, int factsRemoved, long revision) {
  }
}
