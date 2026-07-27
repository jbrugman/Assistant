package nl.llm.storyteller.model;

public record ValidationPromptInput(
    String userInput,
    String draftResponse
) { }
