package nl.llm.storyteller.cli;

import nl.llm.storyteller.core.graph.KnowledgeGraphSnapshot;
import nl.llm.storyteller.core.graph.KnowledgeGraphValidator;
import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.EntityType;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.model.PredicateId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeGraphFormatterTest {
  @TempDir
  Path tempDir;

  @Test
  @DisplayName("""
    Given an empty knowledge graph and its file path,
    When the graph is formatted for terminal display,
    Then the absolute graph path and editing guidance should be shown
    """)
  void formatsEmptyGraphWithPathAndEditingGuidance() {
    Path graphFile = tempDir.resolve("memory/../knowledge-graph.json");

    String formatted = KnowledgeGraphFormatter.format(
      KnowledgeGraphSnapshot.from(KnowledgeGraphDocument.empty(), new KnowledgeGraphValidator()),
      graphFile
    );

    assertEquals(
      "Knowledge graph: " + graphFile.toAbsolutePath().normalize()
        + "\n\nThe graph is empty. Add entities and facts to this JSON file; "
        + "Storyteller loads changes automatically.",
      formatted
    );
  }

  @Test
  @DisplayName("""
    Given a knowledge graph with aliases and active, proposed, positive, and negative facts,
    When the graph is formatted for terminal display,
    Then entities and active facts should be rendered deterministically with their relevant markers
    """)
  void formatsPopulatedGraphDeterministically() {
    EntityId valerie = new EntityId("character.valerie");
    EntityId microphone = new EntityId("item.microphone");
    EntityId piano = new EntityId("item.piano");
    KnowledgeGraphDocument document = new KnowledgeGraphDocument(
      KnowledgeGraphDocument.CURRENT_SCHEMA_VERSION,
      7,
      Map.of(
        piano.value(), new Entity(EntityType.ITEM, "Piano", List.of()),
        valerie.value(), new Entity(EntityType.CHARACTER, "Valerie", List.of("Val")),
        microphone.value(), new Entity(EntityType.ITEM, "Microphone", List.of())
      ),
      List.of(
        fact("fact.positive", valerie, microphone, Polarity.POSITIVE, FactStatus.ACTIVE, true),
        fact("fact.proposed", valerie, piano, Polarity.POSITIVE, FactStatus.PROPOSED, false),
        fact("fact.negative", valerie, piano, Polarity.NEGATIVE, FactStatus.ACTIVE, false)
      )
    );
    KnowledgeGraphSnapshot graph = KnowledgeGraphSnapshot.from(document, new KnowledgeGraphValidator());
    Path graphFile = tempDir.resolve("knowledge-graph.json");

    String formatted = KnowledgeGraphFormatter.format(graph, graphFile);

    assertEquals(
      """
      Knowledge graph: %s
      Revision: 7

      Entities:
      - Valerie [CHARACTER] aliases=[Val]
      - Microphone [ITEM]
      - Piano [ITEM]

      Facts:
      - Valerie NOT POSSESSES Piano
      - Valerie POSSESSES Microphone [hard]""".formatted(graphFile.toAbsolutePath().normalize()),
      formatted
    );
  }

  private Fact fact(
    String id,
    EntityId subject,
    EntityId object,
    Polarity polarity,
    FactStatus status,
    boolean hard
  ) {
    return new Fact(
      id,
      subject,
      new PredicateId("POSSESSES"),
      object,
      polarity,
      status,
      FactSource.MANUAL,
      null,
      hard
    );
  }
}
