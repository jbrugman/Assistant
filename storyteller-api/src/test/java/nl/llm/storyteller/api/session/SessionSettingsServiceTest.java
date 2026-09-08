package nl.llm.storyteller.api.session;

import nl.llm.storyteller.core.graph.KnowledgeGraphValidator;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.db.SessionPrompts;
import nl.llm.storyteller.db.SessionSettings;
import nl.llm.storyteller.db.SessionSettingsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSettingsServiceTest {
  private static final String SESSION_ID = "session-id";
  private static final String EMPTY_GRAPH = """
    {"schemaVersion":1,"revision":0,"entities":{},"facts":[]}
    """;
  private static final String VALID_YAML = """
    fixed_protagonists:
      Valerie:
        role: protagonist
    """;

  @Test
  @DisplayName("""
    Given valid fixed-protagonist YAML,
    When the session prompts are saved,
    Then all normalized prompts should be persisted
    """)
  void shouldSaveValidPrompts() {
    StubRepository repository = new StubRepository(new SessionSettings(
      new SessionPrompts("Old system", VALID_YAML, "Old rules"),
      KnowledgeGraphDocument.empty()
    ));
    SessionSettingsService service = new SessionSettingsService(repository, new KnowledgeGraphValidator());

    service.save(SESSION_ID, " New system ", VALID_YAML, " New rules ", EMPTY_GRAPH);

    assertEquals(new SessionSettings(
      new SessionPrompts("New system", VALID_YAML.strip(), "New rules"),
      KnowledgeGraphDocument.empty()
    ), repository.settings);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "fixed_protagonists:\n  Valerie:\n    role: [protagonist",
    "protagonists:\n  Valerie:\n    role: protagonist"
  })
  @DisplayName("""
    Given invalid fixed-protagonist YAML,
    When the session prompts are saved,
    Then the error should identify a source position and leave the stored prompts unchanged
    """)
  void shouldRejectInvalidYamlWithoutSaving(String yaml) {
    SessionPrompts stored = new SessionPrompts("Old system", VALID_YAML, "Old rules");
    SessionSettings original = new SessionSettings(stored, KnowledgeGraphDocument.empty());
    StubRepository repository = new StubRepository(original);
    SessionSettingsService service = new SessionSettingsService(repository, new KnowledgeGraphValidator());

    InvalidFixedProtagonistsException error = assertThrows(
      InvalidFixedProtagonistsException.class,
      () -> service.save(SESSION_ID, "New system", yaml, "New rules", EMPTY_GRAPH)
    );

    assertTrue(error.line() > 0);
    assertTrue(error.column() > 0);
    assertEquals(original, repository.settings);
  }

  @Test
  @DisplayName("""
    Given invalid knowledge-graph JSON,
    When the session settings are saved,
    Then the graph and prompts should remain unchanged
    """)
  void shouldRejectInvalidKnowledgeGraphWithoutSaving() {
    SessionSettings original = new SessionSettings(
      new SessionPrompts("Old system", VALID_YAML, "Old rules"),
      KnowledgeGraphDocument.empty()
    );
    StubRepository repository = new StubRepository(original);
    SessionSettingsService service = new SessionSettingsService(repository, new KnowledgeGraphValidator());

    assertThrows(
      InvalidKnowledgeGraphException.class,
      () -> service.save(SESSION_ID, "New system", VALID_YAML, "New rules", "{invalid")
    );

    assertEquals(original, repository.settings);
  }

  private static final class StubRepository implements SessionSettingsRepository {
    private SessionSettings settings;

    private StubRepository(SessionSettings settings) {
      this.settings = settings;
    }

    @Override
    public SessionSettings load(String sessionId) {
      return settings;
    }

    @Override
    public void save(String sessionId, SessionSettings settings) {
      this.settings = settings;
    }
  }
}
