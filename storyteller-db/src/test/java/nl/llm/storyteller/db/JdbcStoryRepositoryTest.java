package nl.llm.storyteller.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcStoryRepositoryTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  @DisplayName("""
    Given a story containing more than five exchanges,
    When the newest and preceding web pages are loaded,
    Then each page should contain only its requested messages in chronological order
    """)
  void shouldLoadBoundedMessagePagesBeforeAnExclusiveIndex() {
    Database database = new Database("jdbc:h2:file:" + temporaryDirectory.resolve("story-pages"), "sa", "");
    new SchemaInitializer(database).initialize();
    JdbcSessionRepository sessionRepository = new JdbcSessionRepository(database);
    JdbcStoryRepository storyRepository = new JdbcStoryRepository(database);
    Instant now = Instant.parse("2026-09-07T08:00:00Z");
    sessionRepository.create(new SessionRecord(
      "paged-session", "Paged story", now, now, now, now.plusSeconds(3600), false
    ), SessionPrompts.empty());
    for (int turn = 0; turn < 7; turn++) {
      storyRepository.appendTurn("paged-session", "Prompt " + turn, "Response " + turn, null, now);
    }

    List<StoryMessageRecord> newest = storyRepository.loadMessagesBefore(
      "paged-session", Integer.MAX_VALUE, 10
    );
    List<StoryMessageRecord> older = storyRepository.loadMessagesBefore(
      "paged-session", newest.getFirst().messageIndex(), 10
    );

    assertEquals(10, newest.size());
    assertEquals(4, newest.getFirst().messageIndex());
    assertEquals(13, newest.getLast().messageIndex());
    assertEquals(List.of(0, 1, 2, 3), older.stream().map(StoryMessageRecord::messageIndex).toList());
  }

  @Test
  @DisplayName("""
    Given a story turn with an image,
    When the turn is stored and loaded,
    Then the image should remain attached to the user message only
    """)
  void shouldStoreImageWithUserMessage() {
    Database database = new Database("jdbc:h2:file:" + temporaryDirectory.resolve("story-image"), "sa", "");
    new SchemaInitializer(database).initialize();
    JdbcSessionRepository sessionRepository = new JdbcSessionRepository(database);
    JdbcStoryRepository storyRepository = new JdbcStoryRepository(database);
    Instant now = Instant.parse("2026-09-08T08:00:00Z");
    sessionRepository.create(new SessionRecord(
      "image-session", "Image story", now, now, now, now.plusSeconds(3600), false
    ), SessionPrompts.empty());
    byte[] content = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    StoryTurnRecord turn = storyRepository.appendTurn(
      "image-session", "Describe this", "An image", new StoryImage("image/png", content), now
    );

    List<StoryMessageRecord> messages = storyRepository.loadMessagesBefore(
      "image-session", Integer.MAX_VALUE, 2
    );
    assertTrue(messages.getFirst().hasImage());
    assertFalse(messages.getLast().hasImage());
    assertEquals(new StoryImage("image/png", content), storyRepository.loadImage(
      "image-session", turn.userMessageIndex()
    ).orElseThrow());
    assertTrue(storyRepository.loadRecentMessages("image-session", 2).getFirst().imageDataUrl()
      .startsWith("data:image/png;base64,"));
  }

  @Test
  @DisplayName("""
    Given several persisted story exchanges,
    When selected user-message indexes are loaded as past context,
    Then only complete exchanges owned by that session should be returned chronologically
    """)
  void shouldLoadSelectedPastExchanges() {
    Database database = new Database("jdbc:h2:file:" + temporaryDirectory.resolve("past-exchanges"), "sa", "");
    new SchemaInitializer(database).initialize();
    JdbcSessionRepository sessionRepository = new JdbcSessionRepository(database);
    JdbcStoryRepository storyRepository = new JdbcStoryRepository(database);
    Instant now = Instant.parse("2026-09-08T10:00:00Z");
    sessionRepository.create(new SessionRecord(
      "past-session", "Past story", now, now, now, now.plusSeconds(3600), false
    ), SessionPrompts.empty());
    storyRepository.appendTurn("past-session", "Prompt zero", "Response zero", null, now);
    storyRepository.appendTurn("past-session", "Prompt one", "Response one", null, now);
    storyRepository.appendTurn("past-session", "Prompt two", "Response two", null, now);

    List<PastStoryExchange> exchanges = storyRepository.loadPastExchanges("past-session", List.of(4, 0));

    assertEquals(5, storyRepository.lastMessageIndex("past-session"));
    assertEquals(List.of(0, 4), exchanges.stream().map(PastStoryExchange::messageIndex).toList());
    assertEquals("Prompt zero", exchanges.getFirst().prompt());
    assertEquals("Response two", exchanges.getLast().response());
  }
}
