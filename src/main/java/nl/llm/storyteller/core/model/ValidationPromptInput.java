package nl.llm.storyteller.core.model;

public record ValidationPromptInput(
  String userInput,
  String draftResponse,
  String knowledgeGraphFacts
) { }
