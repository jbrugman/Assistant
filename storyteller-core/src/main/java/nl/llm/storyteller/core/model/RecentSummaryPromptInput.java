package nl.llm.storyteller.core.model;

public record RecentSummaryPromptInput(
  String existingRecentSummary,
  String formattedHistory
) { }
