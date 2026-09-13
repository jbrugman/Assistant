package nl.llm.storyteller.api.story;

import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.db.Database;
import nl.llm.storyteller.db.JdbcSessionBundleRepository;
import nl.llm.storyteller.db.JdbcSessionRepository;
import nl.llm.storyteller.db.JdbcStoryRepository;
import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.EntityType;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.model.PredicateId;
import nl.llm.storyteller.db.JdbcKnowledgeGraphRepository;
import nl.llm.storyteller.db.SchemaInitializer;
import nl.llm.storyteller.db.SessionPrompts;
import nl.llm.storyteller.db.SessionRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionKnowledgeGraphServiceTest {
  private static final String SESSION_ID = "graph-session";
  private static final String GRAPH_RESPONSE = """
    {
      "schemaVersion": 1,
      "revision": 0,
      "entities": {
        "character.alice": {
          "type": "CHARACTER",
          "name": "Alice",
          "aliases": [],
          "source": "TURNBASED"
        },
        "item.compass": {
          "type": "ITEM",
          "name": "Compass",
          "aliases": [],
          "source": "TURNBASED"
        }
      },
      "facts": [{
        "id": "fact.alice_possesses_compass",
        "subject": "character.alice",
        "predicate": "POSSESSES",
        "object": "item.compass",
        "polarity": "POSITIVE",
        "status": "ACTIVE",
        "source": "TURNBASED",
        "sourceTurn": null,
        "hard": false
      }]
    }
    """;

  @TempDir
  Path temporaryDirectory;

  @Test
  @DisplayName("""
    Given an API session with enough completed story turns,
    When its derived state update is requested,
    Then the turn-based knowledge graph should be stored for that session
    """)
  void shouldStoreTurnBasedKnowledgeGraphForApiSession() throws Exception {
    Database database = database();
    createSession(database);
    appendTurns(database);

    try (SessionKnowledgeGraphService service = new SessionKnowledgeGraphService(
      database, AppConfig.load(), (_, _, _) -> GRAPH_RESPONSE
    )) {
      service.requestUpdate(SESSION_ID);
      assertTrue(service.awaitIdle());
    }

    var graph = new JdbcSessionBundleRepository(database).loadKnowledgeGraph(SESSION_ID);
    assertEquals(1, graph.revision());
    assertEquals(2, graph.entities().size());
    assertEquals(1, graph.facts().size());
    assertEquals(3, graph.facts().getFirst().sourceTurn());
  }

  @Test
  @DisplayName("""
    Given a session graph containing fixed and turn-based knowledge,
    When its turn-based items are reset from Story settings,
    Then only the turn-based facts and entities should be removed from H2
    """)
  void shouldResetOnlyTurnBasedItemsForApiSession() throws Exception {
    Database database = database();
    createSession(database);
    var repository = graphRepository(database);
    EntityId valerie = new EntityId("character.valerie");
    EntityId compass = new EntityId("item.compass");
    EntityId letter = new EntityId("item.letter");
    repository.save(new KnowledgeGraphDocument(
      1,
      4,
      Map.of(
        valerie.value(), new Entity(EntityType.CHARACTER, "Valerie", List.of(), FactSource.FIXED_PROTAGONIST),
        compass.value(), new Entity(EntityType.ITEM, "Compass", List.of(), FactSource.FIXED_PROTAGONIST),
        letter.value(), new Entity(EntityType.ITEM, "Letter", List.of(), FactSource.TURNBASED)
      ),
      List.of(
        fact("fact.fixed", valerie, compass, FactSource.FIXED_PROTAGONIST, true),
        fact("fact.turnbased", valerie, letter, FactSource.TURNBASED, false)
      )
    ));

    try (SessionKnowledgeGraphService service = new SessionKnowledgeGraphService(
      database, AppConfig.load(), (_, _, _) -> GRAPH_RESPONSE
    )) {
      var result = service.resetTurnBasedItems(SESSION_ID);

      assertEquals(1, result.entitiesRemoved());
      assertEquals(1, result.factsRemoved());
    }
    KnowledgeGraphDocument graph = repository.load();
    assertEquals(List.of("character.valerie", "item.compass"), graph.entities().keySet().stream().sorted().toList());
    assertEquals(List.of("fact.fixed"), graph.facts().stream().map(Fact::id).toList());
  }

  @Test
  @DisplayName("""
    Given Fixed protagonists stored for an API session in H2,
    When the session knowledge graph is filled,
    Then the stored protagonists should be sent to the model and the generated fixed graph should be saved
    """)
  void shouldFillGraphFromSessionFixedProtagonists() throws Exception {
    Database database = database();
    createSession(database);
    AtomicReference<String> suppliedContext = new AtomicReference<>();

    try (SessionKnowledgeGraphService service = new SessionKnowledgeGraphService(
      database, AppConfig.load(), (messages, _, _) -> {
        suppliedContext.set(messages.getLast().content());
        return GRAPH_RESPONSE;
      }
    )) {
      var result = service.fillFromFixedProtagonists(SESSION_ID);

      assertEquals(2, result.entities());
      assertEquals(1, result.facts());
    }
    assertEquals("Fixed protagonist definitions to process:\n\nFixed protagonists", suppliedContext.get());
    KnowledgeGraphDocument graph = graphRepository(database).load();
    assertEquals(FactSource.FIXED_PROTAGONIST, graph.entities().get("character.alice").source());
    assertEquals(FactSource.FIXED_PROTAGONIST, graph.facts().getFirst().source());
  }

  @Test
  @DisplayName("""
    Given a session knowledge graph containing data,
    When an empty graph is generated from Story settings,
    Then a minimal empty graph with a new revision should be stored in H2
    """)
  void shouldGenerateEmptyGraphForApiSession() throws Exception {
    Database database = database();
    createSession(database);
    var repository = graphRepository(database);
    repository.save(new KnowledgeGraphDocument(
      1,
      6,
      Map.of("character.valerie", new Entity(
        EntityType.CHARACTER, "Valerie", List.of(), FactSource.FIXED_PROTAGONIST
      )),
      List.of()
    ));

    try (SessionKnowledgeGraphService service = new SessionKnowledgeGraphService(
      database, AppConfig.load(), (_, _, _) -> GRAPH_RESPONSE
    )) {
      var graph = service.generateEmpty(SESSION_ID);

      assertEquals(7, graph.revision());
      assertTrue(graph.entities().isEmpty());
      assertTrue(graph.facts().isEmpty());
    }
    assertTrue(repository.load().entities().isEmpty());
    assertTrue(repository.load().facts().isEmpty());
  }

  @Test
  @DisplayName("""
    Given a processed graph that is reset before its last story turn is undone,
    When a replacement turn is completed,
    Then turn-based graph generation should be triggered again
    """)
  void shouldRegenerateAfterResetUndoAndReplacementTurn() throws Exception {
    Database database = database();
    createSession(database);
    JdbcStoryRepository stories = new JdbcStoryRepository(database);
    stories.appendTurn(
      SESSION_ID, "Original prompt", "Original response", null, Instant.parse("2026-09-08T12:00:01Z")
    );
    EntityId alice = new EntityId("character.alice");
    EntityId compass = new EntityId("item.compass");
    graphRepository(database).save(new KnowledgeGraphDocument(
      1,
      1,
      Map.of(
        alice.value(), new Entity(EntityType.CHARACTER, "Alice", List.of(), FactSource.TURNBASED),
        compass.value(), new Entity(EntityType.ITEM, "Compass", List.of(), FactSource.TURNBASED)
      ),
      List.of(new Fact(
        "fact.alice_possesses_compass", alice, new PredicateId("POSSESSES"), compass,
        Polarity.POSITIVE, FactStatus.ACTIVE, FactSource.TURNBASED, 1, false
      ))
    ));
    AtomicInteger generationRequests = new AtomicInteger();

    try (SessionKnowledgeGraphService service = new SessionKnowledgeGraphService(
      database, AppConfig.load(), (_, _, _) -> {
        generationRequests.incrementAndGet();
        return GRAPH_RESPONSE;
      }
    )) {
      service.resetTurnBasedItems(SESSION_ID);

      assertTrue(stories.undoLastTurn(SESSION_ID, Instant.parse("2026-09-08T12:01:00Z")));
      stories.appendTurn(
        SESSION_ID,
        "Replacement prompt",
        "Replacement response",
        null,
        Instant.parse("2026-09-08T12:02:00Z")
      );
      service.requestUpdate(SESSION_ID);
      assertTrue(service.awaitIdle());
    }

    assertEquals(1, generationRequests.get());
    assertEquals(1, graphRepository(database).load().facts().getFirst().sourceTurn());
  }

  private Database database() {
    Database database = new Database(
      "jdbc:h2:file:" + temporaryDirectory.resolve("storyteller"), "sa", ""
    );
    new SchemaInitializer(database).initialize();
    return database;
  }

  private void createSession(Database database) {
    Instant now = Instant.parse("2026-09-08T12:00:00Z");
    new JdbcSessionRepository(database).create(
      new SessionRecord(SESSION_ID, "Graph test", now, now, now, now.plusSeconds(3600), true),
      new SessionPrompts("System", "Fixed protagonists", "Rules")
    );
  }

  private void appendTurns(Database database) {
    JdbcStoryRepository stories = new JdbcStoryRepository(database);
    for (int turn = 1; turn <= 3; turn++) {
      stories.appendTurn(
        SESSION_ID, "Prompt " + turn, "Response " + turn, null,
        Instant.parse("2026-09-08T12:00:00Z").plusSeconds(turn)
      );
    }
  }

  private JdbcKnowledgeGraphRepository graphRepository(Database database) {
    return new JdbcKnowledgeGraphRepository(
      database,
      SESSION_ID,
      new nl.llm.storyteller.core.graph.KnowledgeGraphValidator(
        nl.llm.storyteller.core.graph.PredicateCatalog.load(AppConfig.load().baseDir())
      )
    );
  }

  private Fact fact(String id, EntityId subject, EntityId object, FactSource source, boolean hard) {
    return new Fact(
      id, subject, new PredicateId("POSSESSES"), object, Polarity.POSITIVE,
      FactStatus.ACTIVE, source, null, hard
    );
  }
}
