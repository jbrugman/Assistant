package nl.llm.storyteller.db;

import nl.llm.storyteller.core.model.Message;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StoryRepository {
  List<Message> loadRecentMessages(String sessionId, int maximumMessages);

  List<StoryMessageRecord> loadMessagesBefore(String sessionId, int beforeMessageIndex, int maximumMessages);

  Optional<StoryImage> loadImage(String sessionId, int messageIndex);

  int lastMessageIndex(String sessionId);

  List<PastStoryExchange> loadPastExchanges(String sessionId, List<Integer> messageIndexes);

  StoryTurnRecord appendTurn(
    String sessionId,
    String userInput,
    String assistantResponse,
    StoryImage image,
    Instant updatedAt
  );

  boolean updateAssistantMessage(String sessionId, int messageIndex, String content, Instant updatedAt);

  boolean undoLastTurn(String sessionId, Instant updatedAt);
}
