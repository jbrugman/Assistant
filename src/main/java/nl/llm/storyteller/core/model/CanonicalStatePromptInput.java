package nl.llm.storyteller.core.model;

public record CanonicalStatePromptInput(
  String existingCanonicalState,
  String formattedHistory
) { }
