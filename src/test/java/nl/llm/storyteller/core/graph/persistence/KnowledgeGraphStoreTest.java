package nl.llm.storyteller.core.graph.persistence;

import nl.llm.storyteller.core.graph.KnowledgeGraphValidationException;
import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.EntityType;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactKey;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.model.Predicate;
import nl.llm.storyteller.core.graph.model.TruthValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeGraphStoreTest {
  @TempDir
  Path tempDir;

  @Test
  void missingFileLoadsAnEmptyDocument() {
    KnowledgeGraphStore store = new KnowledgeGraphStore(tempDir.resolve("knowledge-graph.json"));

    assertEquals(KnowledgeGraphDocument.empty(), store.load());
  }

  @Test
  void savesAndLoadsAValidatedDocumentAndSnapshot() throws IOException {
    Path path = tempDir.resolve("memory/knowledge-graph.json");
    KnowledgeGraphStore store = new KnowledgeGraphStore(path);
    KnowledgeGraphDocument document = document();

    store.save(document);

    assertEquals(document, store.load());
    assertEquals(
      TruthValue.FALSE,
      store.loadSnapshot().truthValue(new FactKey(
        new EntityId("character.mike"),
        Predicate.CAN_PERFORM,
        new EntityId("skill.guitar")
      ))
    );
    try (var files = Files.list(path.getParent())) {
      assertFalse(files.anyMatch(candidate -> candidate.getFileName().toString().endsWith(".tmp")));
    }
  }

  @Test
  void rejectsInvalidDocumentBeforeReplacingExistingFile() throws IOException {
    Path path = tempDir.resolve("knowledge-graph.json");
    KnowledgeGraphStore store = new KnowledgeGraphStore(path);
    store.save(document());
    String original = Files.readString(path);

    KnowledgeGraphDocument invalid = new KnowledgeGraphDocument(99, 0, Map.of(), List.of());

    assertThrows(KnowledgeGraphValidationException.class, () -> store.save(invalid));
    assertEquals(original, Files.readString(path));
  }

  @Test
  void reportsMalformedJsonWithTheGraphPath() throws IOException {
    Path path = tempDir.resolve("knowledge-graph.json");
    Files.writeString(path, "{not-json");
    KnowledgeGraphStore store = new KnowledgeGraphStore(path);

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, store::load);

    assertFalse(exception.getMessage().isBlank());
  }

  private KnowledgeGraphDocument document() {
    return new KnowledgeGraphDocument(
      1,
      3,
      Map.of(
        "character.mike", new Entity(EntityType.CHARACTER, "Mike", List.of()),
        "skill.guitar", new Entity(EntityType.SKILL, "Playing guitar", List.of("guitar"))
      ),
      List.of(new Fact(
        "fact.mike_guitar",
        new EntityId("character.mike"),
        Predicate.CAN_PERFORM,
        new EntityId("skill.guitar"),
        Polarity.NEGATIVE,
        FactStatus.ACTIVE,
        FactSource.FIXED_PROTAGONIST,
        null,
        true
      ))
    );
  }
}
