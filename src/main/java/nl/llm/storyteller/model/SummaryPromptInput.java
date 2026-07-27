package nl.llm.storyteller.model;

public record SummaryPromptInput(
  String existingSummary,
  String formattedHistory
) { }
