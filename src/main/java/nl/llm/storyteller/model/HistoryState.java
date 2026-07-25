package nl.llm.storyteller.model;

import java.util.ArrayList;
import java.util.List;

public record HistoryState(
  List<Message> messages,
  int summaryCursor,
  int recentSummaryCursor,
  int canonicalStateCursor
) {
  public static HistoryState empty() {
    return new HistoryState(
      new ArrayList<>(),
      0,
      0,
      0
    );
  }
}
