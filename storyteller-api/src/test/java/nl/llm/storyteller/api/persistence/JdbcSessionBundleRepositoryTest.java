package nl.llm.storyteller.api.persistence;

import nl.llm.storyteller.api.bundle.SessionBundle;
import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.EntityType;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.model.PredicateId;
import nl.llm.storyteller.core.model.HistoryState;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.model.TurnState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JdbcSessionBundleRepositoryTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  @DisplayName("""
    Given a parsed session bundle,
    When it is imported into H2,
    Then history, derived memory, and the normalized knowledge graph should be stored atomically
    """)
  void shouldPersistAndLoadCompleteBundle() {
    Database database = new Database("jdbc:h2:file:" + temporaryDirectory.resolve("bundle"), "sa", "");
    new SchemaInitializer(database).initialize();
    JdbcSessionBundleRepository repository = new JdbcSessionBundleRepository(database);
    Instant now = Instant.parse("2026-09-06T16:00:00Z");
    SessionRecord session = new SessionRecord(
      "imported-session", "Imported", now, now, now, now.plusSeconds(3600), false
    );
    SessionBundle bundle = bundle();

    repository.create(session, bundle);

    assertEquals(bundle, repository.load(session.sessionId()));
  }

  @Test
  @DisplayName("""
    Given an imported session containing knowledge-graph facts,
    When the session is deleted,
    Then its dependent graph data should be removed without a foreign-key failure
    """)
  void shouldDeleteImportedSessionWithKnowledgeGraphFacts() {
    Database database = new Database("jdbc:h2:file:" + temporaryDirectory.resolve("delete-bundle"), "sa", "");
    new SchemaInitializer(database).initialize();
    JdbcSessionBundleRepository bundleRepository = new JdbcSessionBundleRepository(database);
    JdbcSessionRepository sessionRepository = new JdbcSessionRepository(database);
    Instant now = Instant.parse("2026-09-06T16:00:00Z");
    SessionRecord session = new SessionRecord(
      "imported-session", "Imported", now, now, now, now.plusSeconds(3600), false
    );
    bundleRepository.create(session, bundle());

    sessionRepository.delete(session.sessionId());

    assertFalse(sessionRepository.findById(session.sessionId()).isPresent());
  }

  private SessionBundle bundle() {
    var entities = new LinkedHashMap<String, Entity>();
    entities.put("alice", new Entity(EntityType.CHARACTER, "Alice", List.of("Al"), FactSource.MANUAL));
    entities.put("paris", new Entity(EntityType.LOCATION, "Paris", List.of(), FactSource.MANUAL));
    Fact fact = new Fact(
      "alice-lives-paris",
      new EntityId("alice"),
      new PredicateId("LIVES"),
      new EntityId("paris"),
      Polarity.POSITIVE,
      FactStatus.ACTIVE,
      FactSource.MANUAL,
      0,
      true
    );
    return new SessionBundle(
      new HistoryState(
        List.of(new Message("user", "Begin"), new Message("assistant", "Alice wakes.")),
        2,
        2,
        2
      ),
      "Summary",
      "Recent",
      "location: Paris",
      new TurnState("go", true, 2, List.of("Alice"), java.util.Map.of("Alice", 1)),
      new KnowledgeGraphDocument(1, 3, entities, List.of(fact))
    );
  }
}
