package nl.llm.storyteller.core.model;

public record SummaryPromptInput(
  String existingSummary,
  String formattedHistory
) { }
