package nl.llm.storyteller.api.session;

import nl.llm.storyteller.api.persistence.SessionPromptRepository;
import nl.llm.storyteller.api.persistence.SessionPrompts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionPromptServiceTest {
  private static final String SESSION_ID = "session-id";
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
    StubRepository repository = new StubRepository(new SessionPrompts("Old system", VALID_YAML, "Old rules"));
    SessionPromptService service = new SessionPromptService(repository);

    service.save(SESSION_ID, " New system ", VALID_YAML, " New rules ");

    assertEquals(new SessionPrompts("New system", VALID_YAML.strip(), "New rules"), repository.prompts);
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
    StubRepository repository = new StubRepository(stored);
    SessionPromptService service = new SessionPromptService(repository);

    InvalidFixedProtagonistsException error = assertThrows(
      InvalidFixedProtagonistsException.class,
      () -> service.save(SESSION_ID, "New system", yaml, "New rules")
    );

    assertTrue(error.line() > 0);
    assertTrue(error.column() > 0);
    assertEquals(stored, repository.prompts);
  }

  private static final class StubRepository implements SessionPromptRepository {
    private SessionPrompts prompts;

    private StubRepository(SessionPrompts prompts) {
      this.prompts = prompts;
    }

    @Override
    public SessionPrompts load(String sessionId) {
      return prompts;
    }

    @Override
    public void save(String sessionId, SessionPrompts prompts) {
      this.prompts = prompts;
    }
  }
}
