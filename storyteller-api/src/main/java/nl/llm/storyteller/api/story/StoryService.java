package nl.llm.storyteller.api.story;

import nl.llm.storyteller.db.StoryImage;
import nl.llm.storyteller.db.StoryMessageRecord;
import nl.llm.storyteller.db.StoryRepository;

import java.util.List;
import java.util.Optional;

public final class StoryService {
  private final StoryRepository storyRepository;

  public StoryService(StoryRepository storyRepository) {
    this.storyRepository = storyRepository;
  }

  public Optional<StoryImage> loadImage(String sessionId, int messageIndex) {
    return storyRepository.loadImage(sessionId, messageIndex);
  }

  public List<StoryMessageRecord> loadMessagesBefore(String sessionId, int beforeMessageIndex, int maximumMessages) {
    return storyRepository.loadMessagesBefore(sessionId, beforeMessageIndex, maximumMessages);
  }
}
