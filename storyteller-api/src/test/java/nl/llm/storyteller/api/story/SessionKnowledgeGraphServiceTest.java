package nl.llm.storyteller.api.story;

import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.db.Database;
import nl.llm.storyteller.db.JdbcSessionBundleRepository;
import nl.llm.storyteller.db.JdbcSessionRepository;
import nl.llm.storyteller.db.JdbcStoryRepository;
import nl.llm.storyteller.db.SchemaInitializer;
import nl.llm.storyteller.db.SessionPrompts;
import nl.llm.storyteller.db.SessionRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

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
      assertTrue(service.awaitIdle(5, TimeUnit.SECONDS));
    }

    var graph = new JdbcSessionBundleRepository(database).loadKnowledgeGraph(SESSION_ID);
    assertEquals(1, graph.revision());
    assertEquals(2, graph.entities().size());
    assertEquals(1, graph.facts().size());
    assertEquals(3, graph.facts().getFirst().sourceTurn());
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
}
