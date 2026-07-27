package nl.llm.storyteller.model;

public record RecentSummaryPromptInput(
  String existingRecentSummary,
  String formattedHistory
) { }
