package nl.llm.storyteller.db;

import nl.llm.storyteller.core.model.Message;

import java.util.List;

public record SessionMemory(
  String summary,
  String recentSummary,
  String canonicalState,
  int summaryCursor,
  int recentSummaryCursor,
  int canonicalStateCursor,
  List<Message> messages
) {
  public SessionMemory {
    summary = valueOrEmpty(summary);
    recentSummary = valueOrEmpty(recentSummary);
    canonicalState = valueOrEmpty(canonicalState);
    messages = List.copyOf(messages);
  }

  private static String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }
}
