package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.HistoryState;
import nl.llm.storyteller.core.model.Message;

import java.util.List;

public interface StoryHistory {
  HistoryState load();

  void save(HistoryState state);

  void appendTurn(String userInput, String assistantResponse);

  String removeLastTurn();

  LastTurn loadLastTurn();

  List<Message> recentMessages(int limitTurns);

  List<Message> recentMessagesWindow(int totalTurns, int trailingTurnsToExclude);

  void markSummarized(int messagesCount);

  void markRecentSummarized(int messagesCount);

  void markCanonicalStateUpdated(int messagesCount);

  record LastTurn(String userInput, String assistantResponse) {
    public boolean isPresent() {
      return (userInput != null && !userInput.isBlank())
        || (assistantResponse != null && !assistantResponse.isBlank());
    }
  }
}
