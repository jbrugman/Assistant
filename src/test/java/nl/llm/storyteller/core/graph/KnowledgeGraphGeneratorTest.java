package nl.llm.storyteller.core.graph;

import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphStore;
import nl.llm.storyteller.core.service.ChatClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeGraphGeneratorTest {
  @TempDir Path tempDir;

  @Test
  void validatesPersistsAndPublishesModelResult() throws Exception {
    KnowledgeGraphStore store = new KnowledgeGraphStore(tempDir.resolve("graph.json"));
    ReadOnlyKnowledgeGraphService service = new ReadOnlyKnowledgeGraphService(store);
    ChatClient client = (messages, options, timeout) -> """
      {
        "schemaVersion": 99,
        "revision": 99,
        "entities": {
          "character.valerie": {"type":"CHARACTER", "name":"Valerie", "aliases":[]}
        },
        "facts": []
      }
      """;

    var result = new KnowledgeGraphGenerator(client, store, service, Map.of(), 10).generate("fixed data");

    assertEquals(1, result.entities());
    assertEquals(1, result.revision());
    assertEquals(1, store.load().schemaVersion());
    assertEquals(1, service.current().revision());
  }

  @Test
  void keepsExistingGraphWhenModelOutputIsInvalid() {
    KnowledgeGraphStore store = new KnowledgeGraphStore(tempDir.resolve("graph.json"));
    store.save(KnowledgeGraphDocument.empty());
    ReadOnlyKnowledgeGraphService service = new ReadOnlyKnowledgeGraphService(store);
    ChatClient client = (messages, options, timeout) -> "not json";
    KnowledgeGraphGenerator generator = new KnowledgeGraphGenerator(client, store, service, Map.of(), 10);

    assertThrows(IllegalArgumentException.class, () -> generator.generate("fixed data"));
    assertEquals(KnowledgeGraphDocument.empty(), store.load());
    assertEquals(0, service.current().revision());
  }
}
