package nl.llm.storyteller.core;

import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphRepository;
import nl.llm.storyteller.core.service.StoryHistory;
import nl.llm.storyteller.core.service.StoryPromptSource;
import nl.llm.storyteller.core.service.TextMemory;
import nl.llm.storyteller.core.service.TurnStateRepository;

public record ApplicationStorage(
  StoryHistory history,
  TextMemory summary,
  TextMemory recentSummary,
  TextMemory canonicalState,
  TurnStateRepository turnState,
  KnowledgeGraphRepository knowledgeGraph,
  StoryPromptSource prompts
) {
}
