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

  @Test
  @DisplayName("""
    Given a session with mid-term memory,
    When the mid-term memory is reset,
    Then the recent summary and cursor should be cleared
    """)
  void shouldResetMidTermMemory() throws Exception {
    List<Message> messages = new ArrayList<>();
    for (int turn = 1; turn <= 18; turn++) {
      messages.add(new Message("user", "Prompt " + turn));
      messages.add(new Message("assistant", "Response " + turn));
    }
    SessionMemory initialMemory = new SessionMemory(
      "Existing mid-term summary", "Existing recent summary", "Existing canonical",
      10, 32, 36, messages
    );
    MemoryRepository memoryRepository = new MemoryRepository(initialMemory);
    SettingsRepository settingsRepository = new SettingsRepository();
    String sessionId = "session-id";

    try (SessionMemoryService service = new SessionMemoryService(
      memoryRepository, settingsRepository, AppConfig.load(), new SequencedChatClient(List.of())
    )) {
      service.resetMidTermMemory(sessionId);
    }

    SessionMemory stored = memoryRepository.load(sessionId);
    assertEquals("Existing mid-term summary", stored.summary());
    assertEquals(10, stored.summaryCursor());
    assertEquals("", stored.recentSummary());
    assertEquals(0, stored.recentSummaryCursor());
    assertEquals("Existing canonical", stored.canonicalState());
    assertEquals(36, stored.canonicalStateCursor());
  }

  @Test
  @DisplayName("""
    Given a session with long-term memory,
    When the long-term memory is reset,
    Then the summary and cursor should be cleared
    """)
  void shouldResetLongTermMemory() throws Exception {
    List<Message> messages = new ArrayList<>();
    for (int turn = 1; turn <= 18; turn++) {
      messages.add(new Message("user", "Prompt " + turn));
      messages.add(new Message("assistant", "Response " + turn));
    }
    SessionMemory initialMemory = new SessionMemory(
      "Existing mid-term summary", "Existing recent summary", "Existing canonical",
      10, 32, 36, messages
    );
    MemoryRepository memoryRepository = new MemoryRepository(initialMemory);
    SettingsRepository settingsRepository = new SettingsRepository();
    String sessionId = "session-id";

    try (SessionMemoryService service = new SessionMemoryService(
      memoryRepository, settingsRepository, AppConfig.load(), new SequencedChatClient(List.of())
    )) {
      service.resetLongTermMemory(sessionId);
    }

    SessionMemory stored = memoryRepository.load(sessionId);
    assertEquals("", stored.summary());
    assertEquals(0, stored.summaryCursor());
    assertEquals("Existing recent summary", stored.recentSummary());
    assertEquals(32, stored.recentSummaryCursor());
    assertEquals("Existing canonical", stored.canonicalState());
    assertEquals(36, stored.canonicalStateCursor());
  }

  @Test
  @DisplayName("""
    Given a session with canonical state,
    When the canonical state is reset,
    Then the canonical state and cursor should be cleared
    """)
  void shouldResetCanonicalState() throws Exception {
    List<Message> messages = new ArrayList<>();
    for (int turn = 1; turn <= 18; turn++) {
      messages.add(new Message("user", "Prompt " + turn));
      messages.add(new Message("assistant", "Response " + turn));
    }
    SessionMemory initialMemory = new SessionMemory(
      "Existing mid-term summary", "Existing recent summary", "Existing canonical",
      10, 32, 36, messages
    );
    MemoryRepository memoryRepository = new MemoryRepository(initialMemory);
    SettingsRepository settingsRepository = new SettingsRepository();
    String sessionId = "session-id";

    try (SessionMemoryService service = new SessionMemoryService(
      memoryRepository, settingsRepository, AppConfig.load(), new SequencedChatClient(List.of())
    )) {
      service.resetCanonicalState(sessionId);
    }

    SessionMemory stored = memoryRepository.load(sessionId);
    assertEquals("Existing mid-term summary", stored.summary());
    assertEquals(10, stored.summaryCursor());
    assertEquals("Existing recent summary", stored.recentSummary());
    assertEquals(32, stored.recentSummaryCursor());
    assertEquals("", stored.canonicalState());
    assertEquals(0, stored.canonicalStateCursor());
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

    @Override
    public boolean resetMidTermMemory(String sessionId) {
      memory = new SessionMemory(
        memory.summary(), "", memory.canonicalState(), memory.summaryCursor(),
        0, memory.canonicalStateCursor(), memory.messages()
      );
      return true;
    }

    @Override
    public boolean resetLongTermMemory(String sessionId) {
      memory = new SessionMemory(
        "", memory.recentSummary(), memory.canonicalState(), 0,
        memory.recentSummaryCursor(), memory.canonicalStateCursor(), memory.messages()
      );
      return true;
    }

    @Override
    public boolean resetCanonicalState(String sessionId) {
      memory = new SessionMemory(
        memory.summary(), memory.recentSummary(), "", memory.summaryCursor(),
        memory.recentSummaryCursor(), 0, memory.messages()
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

    @Override
    public Double getTemperature(String sessionId) {
      return null;
    }

    @Override
    public void setTemperature(String sessionId, Double temperature) {
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
