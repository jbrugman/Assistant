package nl.llm.storyteller.api.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
      storyRepository.appendTurn("paged-session", "Prompt " + turn, "Response " + turn, now);
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
}
