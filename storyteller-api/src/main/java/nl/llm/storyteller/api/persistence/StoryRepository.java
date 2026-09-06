package nl.llm.storyteller.api.persistence;

import nl.llm.storyteller.core.model.Message;

import java.time.Instant;
import java.util.List;

public interface StoryRepository {
  List<Message> loadMessages(String sessionId);

  List<Message> loadRecentMessages(String sessionId, int maximumMessages);

  StoryTurnRecord appendTurn(String sessionId, String userInput, String assistantResponse, Instant updatedAt);
}
