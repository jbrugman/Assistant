package nl.llm.storyteller.core.graph;

import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.EntityType;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactKey;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.model.PredicateId;
import nl.llm.storyteller.core.graph.model.TruthValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeGraphSnapshotTest {
  @Test
  @DisplayName("""
    Given a valid knowledge graph document with entities, aliases, and facts,
    When a snapshot is created,
    Then its entities, aliases, and facts should be indexed
    """)
  void indexesEntitiesAliasesAndFacts() {
    KnowledgeGraphSnapshot snapshot = KnowledgeGraphSnapshot.from(document(), new KnowledgeGraphValidator());

    EntityId valerie = new EntityId("character.valerie");
    EntityId microphone = new EntityId("item.microphone");
    assertEquals(7, snapshot.revision());
    assertEquals("Valerie", snapshot.entity(valerie).orElseThrow().name());
    assertEquals(List.of(valerie), snapshot.resolveAlias("  VAL  ").stream().toList());
    assertEquals(1, snapshot.factsBySubject(valerie).size());
    assertEquals(1, snapshot.factsByObject(microphone).size());
    assertEquals(
      TruthValue.TRUE,
      snapshot.truthValue(new FactKey(valerie, new PredicateId("POSSESSES"), microphone))
    );
  }

  @Test
  @DisplayName("""
    Given a knowledge graph without a matching fact,
    When its truth value is requested,
    Then the result should be unknown rather than false
    """)
  void missingFactIsUnknownRatherThanFalse() {
    KnowledgeGraphSnapshot snapshot = KnowledgeGraphSnapshot.from(document(), new KnowledgeGraphValidator());

    TruthValue value = snapshot.truthValue(new FactKey(
      new EntityId("character.valerie"),
      new PredicateId("POSSESSES"),
      new EntityId("item.keyboard")
    ));

    assertEquals(TruthValue.UNKNOWN, value);
  }

  @Test
  @DisplayName("""
    Given a knowledge graph snapshot,
    When a caller attempts to modify an exposed collection,
    Then the modification should be rejected
    """)
  void exposedCollectionsAreImmutable() {
    KnowledgeGraphSnapshot snapshot = KnowledgeGraphSnapshot.from(document(), new KnowledgeGraphValidator());

    assertThrows(
      UnsupportedOperationException.class,
      () -> snapshot.entities().clear()
    );
    assertThrows(
      UnsupportedOperationException.class,
      () -> snapshot.factsBySubject(new EntityId("character.valerie")).clear()
    );
  }

  private KnowledgeGraphDocument document() {
    return new KnowledgeGraphDocument(
      1,
      7,
      Map.of(
        "character.valerie", new Entity(EntityType.CHARACTER, "Valerie", List.of("Val")),
        "item.microphone", new Entity(EntityType.ITEM, "Microphone", List.of()),
        "item.keyboard", new Entity(EntityType.ITEM, "Keyboard", List.of())
      ),
      List.of(new Fact(
        "fact.valerie_microphone",
        new EntityId("character.valerie"),
        new PredicateId("POSSESSES"),
        new EntityId("item.microphone"),
        Polarity.POSITIVE,
        FactStatus.ACTIVE,
        FactSource.MANUAL,
        null,
        true
      ))
    );
  }
}
