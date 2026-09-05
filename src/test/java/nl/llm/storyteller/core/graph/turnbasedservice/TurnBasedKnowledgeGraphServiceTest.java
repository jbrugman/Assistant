package nl.llm.storyteller.core.graph.turnbasedservice;

import nl.llm.storyteller.core.graph.PredicateCatalog;
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
import nl.llm.storyteller.core.graph.service.ReadOnlyKnowledgeGraphService;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.service.ChatClient;
import nl.llm.storyteller.core.service.DerivedMemoryTaskQueue;
import nl.llm.storyteller.core.service.HistoryStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnBasedKnowledgeGraphServiceTest {
  @TempDir
  Path tempDir;

  @Test
  @DisplayName("""
    Given a turn-based graph batch size of three turns,
    When three completed story turns have been persisted,
    Then exactly that batch should be extracted and stored as lower-authority TURNBASED graph data
    """)
  void updatesGraphAfterConfiguredNumberOfTurns() throws Exception {
    TestContext context = context();
    AtomicReference<List<Message>> request = new AtomicReference<>();
    CountDownLatch completed = new CountDownLatch(1);
    ChatClient client = (messages, options, timeout) -> {
      request.set(messages);
      completed.countDown();
      return """
        {
          "schemaVersion": 1,
          "revision": 0,
          "entities": {
            "character.valerie": {"type":"CHARACTER", "name":"Valerie", "aliases":[]},
            "item.microphone": {"type":"ITEM", "name":"Microphone", "aliases":[]}
          },
          "facts": [{
            "id":"fact.valerie_microphone",
            "subject":"character.valerie",
            "predicate":"POSSESSES",
            "object":"item.microphone",
            "polarity":"POSITIVE"
          }]
        }
        """;
    };
    try (DerivedMemoryTaskQueue queue = new DerivedMemoryTaskQueue()) {
      TurnBasedKnowledgeGraphService service = service(context, client, queue, 3);
      appendTurn(context.historyStore(), 1);
      service.startUpdateIfNeeded();
      appendTurn(context.historyStore(), 2);
      service.startUpdateIfNeeded();

      assertFalse(completed.await(100, TimeUnit.MILLISECONDS));

      appendTurn(context.historyStore(), 3);
      service.startUpdateIfNeeded();

      assertTrue(completed.await(5, TimeUnit.SECONDS));
      awaitRevision(context.store(), 1);
      KnowledgeGraphDocument graph = context.store().load();
      assertEquals(FactSource.TURNBASED, graph.entities().get("character.valerie").source());
      assertEquals(FactSource.TURNBASED, graph.entities().get("item.microphone").source());
      assertEquals(FactSource.TURNBASED, graph.facts().getFirst().source());
      assertEquals(3, graph.facts().getFirst().sourceTurn());
      assertFalse(graph.facts().getFirst().hard());
      String systemPrompt = request.get().getFirst().content();
      String prompt = request.get().getLast().content();
      assertTrue(systemPrompt.contains("Treat interactions as events, not proof of an enduring interpersonal relationship"));
      assertTrue(systemPrompt.contains("Talking,\nflirting, kissing"));
      assertTrue(systemPrompt.contains("When in doubt, omit it"));
      assertTrue(systemPrompt.contains("Represent clothing with WEARS from a CHARACTER to an ITEM"));
      assertTrue(systemPrompt.contains("one ITEM entity and one\nWEARS fact per distinct garment"));
      assertTrue(prompt.contains("USER: Turn 1"));
      assertTrue(prompt.contains("ASSISTANT: Reply 3"));
    }
  }

  @Test
  @DisplayName("""
    Given fixed-protagonist graph data and a conflicting turn-based extraction,
    When the extracted graph is merged,
    Then fixed entities and facts should remain unchanged
    """)
  void preservesHigherAuthorityGraphDataDuringMerge() {
    TestContext context = context();
    EntityId valerie = new EntityId("character.valerie");
    EntityId microphone = new EntityId("item.microphone");
    EntityId chris = new EntityId("character.chris");
    EntityId piano = new EntityId("item.piano");
    Fact fixedFact = fact("fact.fixed", valerie, microphone, Polarity.POSITIVE, FactSource.FIXED_PROTAGONIST, true);
    Fact manualFact = fact("fact.manual", chris, piano, Polarity.POSITIVE, FactSource.MANUAL, true);
    KnowledgeGraphDocument current = new KnowledgeGraphDocument(
      1,
      4,
      Map.of(
        valerie.value(), new Entity(EntityType.CHARACTER, "Valerie", List.of(), FactSource.FIXED_PROTAGONIST),
        microphone.value(), new Entity(EntityType.ITEM, "Microphone", List.of(), FactSource.FIXED_PROTAGONIST),
        chris.value(), new Entity(EntityType.CHARACTER, "Chris", List.of(), FactSource.MANUAL),
        piano.value(), new Entity(EntityType.ITEM, "Piano", List.of(), FactSource.MANUAL)
      ),
      List.of(fixedFact, manualFact)
    );
    KnowledgeGraphDocument candidate = new KnowledgeGraphDocument(
      1,
      0,
      Map.of(
        valerie.value(), new Entity(EntityType.CHARACTER, "Wrong name", List.of(), FactSource.TURNBASED),
        microphone.value(), new Entity(EntityType.ITEM, "Wrong item", List.of(), FactSource.TURNBASED)
      ),
      List.of(fact("fact.generated", valerie, microphone, Polarity.NEGATIVE, FactSource.TURNBASED, false))
    );
    try (DerivedMemoryTaskQueue queue = new DerivedMemoryTaskQueue()) {
      TurnBasedKnowledgeGraphService service = service(context, (messages, options, timeout) -> "", queue, 3);

      KnowledgeGraphDocument merged = service.merge(current, candidate, 3);

      assertEquals("Valerie", merged.entities().get(valerie.value()).name());
      assertEquals("Microphone", merged.entities().get(microphone.value()).name());
      assertEquals(current.entities().get(chris.value()), merged.entities().get(chris.value()));
      assertEquals(current.entities().get(piano.value()), merged.entities().get(piano.value()));
      assertEquals(List.of(fixedFact, manualFact), merged.facts());
    }
  }

  @Test
  @DisplayName("""
    Given a turn-based shirt and an authoritative coat worn by a character,
    When a later turn-based clothing snapshot contains only a sweater,
    Then the shirt should disappear while the authoritative coat remains
    """)
  void replacesOnlyTurnBasedClothingForUpdatedCharacter() {
    TestContext context = context();
    EntityId valerie = new EntityId("character.valerie");
    EntityId shirt = new EntityId("item.shirt");
    EntityId coat = new EntityId("item.coat");
    EntityId sweater = new EntityId("item.sweater");
    Fact oldShirt = fact("fact.shirt", valerie, new PredicateId("WEARS"), shirt,
      Polarity.POSITIVE, FactSource.TURNBASED, false);
    Fact fixedCoat = fact("fact.coat", valerie, new PredicateId("WEARS"), coat,
      Polarity.POSITIVE, FactSource.FIXED_PROTAGONIST, true);
    KnowledgeGraphDocument current = new KnowledgeGraphDocument(1, 4, Map.of(
      valerie.value(), new Entity(EntityType.CHARACTER, "Valerie", List.of(), FactSource.FIXED_PROTAGONIST),
      shirt.value(), new Entity(EntityType.ITEM, "overhemd", List.of(), FactSource.TURNBASED),
      coat.value(), new Entity(EntityType.ITEM, "jas", List.of(), FactSource.FIXED_PROTAGONIST)
    ), List.of(oldShirt, fixedCoat));
    KnowledgeGraphDocument candidate = new KnowledgeGraphDocument(1, 0, Map.of(
      sweater.value(), new Entity(EntityType.ITEM, "trui", List.of(), FactSource.TURNBASED)
    ), List.of(fact("fact.sweater", valerie, new PredicateId("WEARS"), sweater,
      Polarity.POSITIVE, FactSource.TURNBASED, false)));
    try (DerivedMemoryTaskQueue queue = new DerivedMemoryTaskQueue()) {
      TurnBasedKnowledgeGraphService service = service(context, (messages, options, timeout) -> "", queue, 3);

      KnowledgeGraphDocument merged = service.merge(current, candidate, 6);

      assertFalse(merged.entities().containsKey(shirt.value()));
      assertTrue(merged.entities().containsKey(sweater.value()));
      assertTrue(merged.facts().contains(fixedCoat));
      assertFalse(merged.facts().contains(oldShirt));
      assertTrue(merged.facts().stream().anyMatch(fact ->
        WEARS_ID.equals(fact.predicate()) && sweater.equals(fact.object())));
    }
  }

  private static final PredicateId WEARS_ID = new PredicateId("WEARS");

  private TestContext context() {
    KnowledgeGraphStore store = new KnowledgeGraphStore(tempDir.resolve("knowledge-graph.json"));
    return new TestContext(
      new HistoryStore(tempDir.resolve("history.json"), tempDir.resolve("history.md")),
      store,
      new ReadOnlyKnowledgeGraphService(store),
      PredicateCatalog.load(Path.of(System.getProperty("user.dir")).toAbsolutePath())
    );
  }

  private TurnBasedKnowledgeGraphService service(
    TestContext context,
    ChatClient client,
    DerivedMemoryTaskQueue queue,
    int batchTurns
  ) {
    return new TurnBasedKnowledgeGraphService(
      context.historyStore(), client, context.store(), context.graphService(), context.predicates(), queue,
      batchTurns, Map.of(), 10
    );
  }

  private void appendTurn(HistoryStore historyStore, int turn) {
    historyStore.appendTurn("Turn " + turn, "Reply " + turn);
  }

  private Fact fact(
    String id,
    EntityId subject,
    EntityId object,
    Polarity polarity,
    FactSource source,
    boolean hard
  ) {
    return fact(id, subject, new PredicateId("POSSESSES"), object, polarity, source, hard);
  }

  private Fact fact(
    String id,
    EntityId subject,
    PredicateId predicate,
    EntityId object,
    Polarity polarity,
    FactSource source,
    boolean hard
  ) {
    return new Fact(
      id, subject, predicate, object, polarity,
      FactStatus.ACTIVE, source, null, hard
    );
  }

  private void awaitRevision(KnowledgeGraphStore store, long revision) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (store.load().revision() != revision) {
      if (System.nanoTime() >= deadline) {
        throw new AssertionError("Knowledge graph was not updated within the timeout.");
      }
      Thread.sleep(10);
    }
  }

  private record TestContext(
    HistoryStore historyStore,
    KnowledgeGraphStore store,
    ReadOnlyKnowledgeGraphService graphService,
    PredicateCatalog predicates
  ) {
  }
}
