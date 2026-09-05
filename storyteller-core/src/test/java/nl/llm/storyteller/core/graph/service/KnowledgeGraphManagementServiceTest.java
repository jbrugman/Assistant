package nl.llm.storyteller.core.graph.service;

import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.EntityType;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.model.PredicateId;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeGraphManagementServiceTest {
  @TempDir
  Path tempDir;

  @Test
  @DisplayName("""
    Given a graph containing fixed, manual, and turn-based entities and facts,
    When turn-based graph data is reset,
    Then only items whose source is TURNBASED should be removed
    """)
  void resetsOnlyTurnBasedGraphItems() {
    KnowledgeGraphStore store = new KnowledgeGraphStore(tempDir.resolve("knowledge-graph.json"));
    ReadOnlyKnowledgeGraphService graphService = new ReadOnlyKnowledgeGraphService(store);
    EntityId valerie = new EntityId("character.valerie");
    EntityId microphone = new EntityId("item.microphone");
    EntityId piano = new EntityId("item.piano");
    EntityId guitar = new EntityId("item.guitar");
    store.save(new KnowledgeGraphDocument(
      1,
      8,
      Map.of(
        valerie.value(), new Entity(EntityType.CHARACTER, "Valerie", List.of(), FactSource.FIXED_PROTAGONIST),
        microphone.value(), new Entity(EntityType.ITEM, "Microphone", List.of(), FactSource.FIXED_PROTAGONIST),
        piano.value(), new Entity(EntityType.ITEM, "Piano", List.of(), FactSource.MANUAL),
        guitar.value(), new Entity(EntityType.ITEM, "Guitar", List.of(), FactSource.TURNBASED)
      ),
      List.of(
        fact("fact.fixed", valerie, microphone, FactSource.FIXED_PROTAGONIST, true),
        fact("fact.manual", valerie, piano, FactSource.MANUAL, true),
        fact("fact.turnbased", valerie, guitar, FactSource.TURNBASED, false)
      )
    ));
    graphService.refresh();
    KnowledgeGraphManagementService service = new KnowledgeGraphManagementService(store, graphService);

    KnowledgeGraphManagementService.ResetResult result = service.resetTurnBasedItems();

    KnowledgeGraphDocument graph = store.load();
    assertEquals(1, result.entitiesRemoved());
    assertEquals(1, result.factsRemoved());
    assertEquals(9, result.revision());
    assertEquals(List.of("character.valerie", "item.microphone", "item.piano"), graph.entities().keySet().stream().sorted().toList());
    assertEquals(List.of("fact.fixed", "fact.manual"), graph.facts().stream().map(Fact::id).toList());
    assertEquals(9, graphService.current().revision());
  }

  private Fact fact(String id, EntityId subject, EntityId object, FactSource source, boolean hard) {
    return new Fact(
      id, subject, new PredicateId("POSSESSES"), object, Polarity.POSITIVE,
      FactStatus.ACTIVE, source, null, hard
    );
  }
}
