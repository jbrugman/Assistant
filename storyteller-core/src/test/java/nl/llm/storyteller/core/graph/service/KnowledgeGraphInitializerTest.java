package nl.llm.storyteller.core.graph.service;

import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeGraphInitializerTest {
  @TempDir Path tempDir;

  @Test
  @DisplayName("""
    Given a knowledge graph store without a graph,
    When an empty graph is generated,
    Then the empty graph should be persisted and published
    """)
  void generatesPersistsAndPublishesEmptyGraph() {
    Path path = tempDir.resolve("knowledge-graph.json");
    KnowledgeGraphStore store = new KnowledgeGraphStore(path);
    ReadOnlyKnowledgeGraphService service = new ReadOnlyKnowledgeGraphService(store);

    var document = new KnowledgeGraphInitializer(store, service).generateEmpty();

    assertTrue(Files.exists(path));
    assertEquals(1, document.schemaVersion());
    assertEquals(1, document.revision());
    assertTrue(document.entities().isEmpty());
    assertTrue(document.facts().isEmpty());
    assertEquals(1, service.current().revision());
  }
}
