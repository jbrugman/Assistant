package nl.llm.storyteller.core.model;

import java.util.List;

public record StoryChatPromptInput(
  String userInput,
  String canonicalState,
  String summary,
  String recentSummary,
  String knowledgeGraphFacts,
  List<Message> recentMessages,
  String extraSystemInstruction
) { }
