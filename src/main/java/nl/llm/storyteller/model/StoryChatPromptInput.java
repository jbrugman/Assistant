package nl.llm.storyteller.model;

import java.util.List;

public record StoryChatPromptInput(
    String userInput,
    String canonicalState,
    String summary,
    String recentSummary,
    List<Message> recentMessages,
    String extraSystemInstruction
) { }
