package nl.llm.storyteller.api.bundle;

import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.model.HistoryState;
import nl.llm.storyteller.core.model.TurnState;

public record SessionBundle(
  HistoryState history,
  String summary,
  String recentSummary,
  String canonicalState,
  TurnState turnState,
  KnowledgeGraphDocument knowledgeGraph
) { }
