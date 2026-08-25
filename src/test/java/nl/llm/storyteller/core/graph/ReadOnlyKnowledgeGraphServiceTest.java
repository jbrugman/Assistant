package nl.llm.storyteller.core.graph;

import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.EntityType;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.model.PredicateId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadOnlyKnowledgeGraphServiceTest {
  @TempDir Path tempDir;
  private final EntityId mike = new EntityId("mike");
  private final EntityId chris = new EntityId("chris");
  private final EntityId guitar = new EntityId("guitar");
  private final EntityId piano = new EntityId("piano");

  @Test
  void shouldSelectActiveHardFactsForMentionedNameOrAlias() {
    ReadOnlyKnowledgeGraphService service = service();

    String facts = service.relevantFacts("Mikey enters the room.");

    assertTrue(facts.contains("Mike possesses Guitar."));
    assertTrue(facts.contains("Mike does not possess Piano."));
    assertFalse(facts.contains("Chris"));
    assertFalse(facts.contains("proposed"));
  }

  @Test
  void shouldNotMatchNamesInsideOtherWordsOrReturnUnrelatedFacts() {
    ReadOnlyKnowledgeGraphService service = service();

    assertEquals("", service.relevantFacts("A microphone lies on the floor."));
    assertEquals("", service.relevantFacts("Nobody enters the room."));
  }

  @Test
  void automaticallyLoadsGraphFileCreatedAfterStartup() {
    KnowledgeGraphStore store = new KnowledgeGraphStore(tempDir.resolve("knowledge-graph.json"));
    ReadOnlyKnowledgeGraphService service = new ReadOnlyKnowledgeGraphService(store);
    store.save(graphDocument());

    assertTrue(service.relevantFacts("Mike enters.").contains("Mike possesses Guitar."));
  }

  @Test
  void rendersDirectionalPositiveAndNegativeRomanticRelationships() {
    EntityId valerie = new EntityId("valerie");
    KnowledgeGraphDocument document = new KnowledgeGraphDocument(
      KnowledgeGraphDocument.CURRENT_SCHEMA_VERSION,
      1,
      Map.of(
        "valerie", new Entity(EntityType.CHARACTER, "Valerie", List.of()),
        "mike", new Entity(EntityType.CHARACTER, "Mike", List.of()),
        "chris", new Entity(EntityType.CHARACTER, "Chris", List.of())
      ),
      List.of(
        new Fact("valerie-loves-mike", valerie, new PredicateId("LOVES"), mike, Polarity.POSITIVE,
          FactStatus.ACTIVE, FactSource.MANUAL, null, true),
        new Fact("valerie-not-loves-chris", valerie, new PredicateId("LOVES"), chris, Polarity.NEGATIVE,
          FactStatus.ACTIVE, FactSource.MANUAL, null, true)
      )
    );
    ReadOnlyKnowledgeGraphService service = new ReadOnlyKnowledgeGraphService(
      KnowledgeGraphSnapshot.from(document, new KnowledgeGraphValidator())
    );

    String facts = service.relevantFacts("Valerie enters.");

    assertTrue(facts.contains("Valerie loves Mike."));
    assertTrue(facts.contains("Valerie does not love Chris."));
  }

  private ReadOnlyKnowledgeGraphService service() {
    KnowledgeGraphDocument document = graphDocument();
    return new ReadOnlyKnowledgeGraphService(KnowledgeGraphSnapshot.from(document, new KnowledgeGraphValidator()));
  }

  private KnowledgeGraphDocument graphDocument() {
    return new KnowledgeGraphDocument(
      KnowledgeGraphDocument.CURRENT_SCHEMA_VERSION,
      1,
      Map.of(
        "mike", new Entity(EntityType.CHARACTER, "Mike", List.of("Mikey")),
        "chris", new Entity(EntityType.CHARACTER, "Chris", List.of()),
        "guitar", new Entity(EntityType.ITEM, "Guitar", List.of()),
        "piano", new Entity(EntityType.ITEM, "Piano", List.of())
      ),
      List.of(
        fact("mike-guitar", mike, guitar, Polarity.POSITIVE, FactStatus.ACTIVE, true),
        fact("mike-no-piano", mike, piano, Polarity.NEGATIVE, FactStatus.ACTIVE, true),
        fact("chris-piano", chris, piano, Polarity.POSITIVE, FactStatus.ACTIVE, true),
        fact("mike-proposed-piano", mike, piano, Polarity.POSITIVE, FactStatus.PROPOSED, false)
      )
    );
  }

  private Fact fact(
    String id, EntityId subject, EntityId object, Polarity polarity, FactStatus status, boolean hard
  ) {
    return new Fact(id, subject, new PredicateId("POSSESSES"), object, polarity, status, FactSource.MANUAL, null, hard);
  }
}
