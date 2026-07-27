package nl.llm.storyteller.model;

public record CanonicalStatePromptInput(
  String existingCanonicalState,
  String formattedHistory
) { }
