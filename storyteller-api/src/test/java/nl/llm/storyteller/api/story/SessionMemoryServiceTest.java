package nl.llm.storyteller.api.story;

import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.service.ChatClient;
import nl.llm.storyteller.db.SessionMemory;
import nl.llm.storyteller.db.SessionMemoryRepository;
import nl.llm.storyteller.db.SessionPrompts;
import nl.llm.storyteller.db.SessionSettings;
import nl.llm.storyteller.db.SessionSettingsRepository;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionMemoryServiceTest {
  @Test
  @DisplayName("""
    Given enough database-backed story turns for every memory window,
    When the session memory refresh runs,
    Then it should persist long-term, mid-term, and canonical memory with their cursors
    """)
  void shouldRefreshAllDerivedMemory() throws Exception {
    List<Message> messages = new ArrayList<>();
    for (int turn = 1; turn <= 18; turn++) {
      messages.add(new Message("user", "Prompt " + turn));
      messages.add(new Message("assistant", "Response " + turn));
    }
    MemoryRepository memoryRepository = new MemoryRepository(new SessionMemory("", "", "", 0, 0, 0, messages));
    SettingsRepository settingsRepository = new SettingsRepository();
    SequencedChatClient client = new SequencedChatClient(List.of("Long history", "Mid-term history", "Canonical"));

    try (SessionMemoryService service = new SessionMemoryService(
      memoryRepository, settingsRepository, AppConfig.load(), client
    )) {
      service.refresh("session-id");
    }

    SessionMemory stored = memoryRepository.load("session-id");
    assertEquals("Long history", stored.summary());
    assertEquals("Mid-term history", stored.recentSummary());
    assertEquals("Canonical", stored.canonicalState());
    assertEquals(12, stored.summaryCursor());
    assertEquals(32, stored.recentSummaryCursor());
    assertEquals(36, stored.canonicalStateCursor());
    assertEquals(3, client.requests.size());
    assertTrue(client.requests.stream().allMatch(request -> request.getFirst().content().contains("Edited fixed")));
  }

  private static final class MemoryRepository implements SessionMemoryRepository {
    private SessionMemory memory;

    private MemoryRepository(SessionMemory memory) {
      this.memory = memory;
    }

    @Override
    public SessionMemory load(String sessionId) {
      return new SessionMemory(
        memory.summary(), memory.recentSummary(), memory.canonicalState(), memory.summaryCursor(),
        memory.recentSummaryCursor(), memory.canonicalStateCursor(), List.of()
      );
    }

    @Override
    public SessionMemory loadForUpdate(String sessionId) {
      return memory;
    }

    @Override
    public boolean updateSummary(String sessionId, SessionMemory expected, String content, int cursor) {
      memory = new SessionMemory(
        content, memory.recentSummary(), memory.canonicalState(), cursor,
        memory.recentSummaryCursor(), memory.canonicalStateCursor(), memory.messages()
      );
      return true;
    }

    @Override
    public boolean updateRecentSummary(String sessionId, SessionMemory expected, String content, int cursor) {
      memory = new SessionMemory(
        memory.summary(), content, memory.canonicalState(), memory.summaryCursor(),
        cursor, memory.canonicalStateCursor(), memory.messages()
      );
      return true;
    }

    @Override
    public boolean updateCanonicalState(String sessionId, SessionMemory expected, String content, int cursor) {
      memory = new SessionMemory(
        memory.summary(), memory.recentSummary(), content, memory.summaryCursor(),
        memory.recentSummaryCursor(), cursor, memory.messages()
      );
      return true;
    }
  }

  private static final class SettingsRepository implements SessionSettingsRepository {
    @Override
    public SessionSettings load(String sessionId) {
      return new SessionSettings(
        new SessionPrompts("System", "Edited fixed", "Rules"),
        KnowledgeGraphDocument.empty()
      );
    }

    @Override
    public void save(String sessionId, SessionSettings settings) {
    }
  }

  private static final class SequencedChatClient implements ChatClient {
    private final List<String> responses;
    private final List<List<Message>> requests = new ArrayList<>();

    private SequencedChatClient(List<String> responses) {
      this.responses = responses;
    }

    @Override
    public String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds) {
      requests.add(List.copyOf(messages));
      return responses.get(requests.size() - 1);
    }
  }
}
