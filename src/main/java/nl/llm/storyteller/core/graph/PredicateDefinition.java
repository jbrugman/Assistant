package nl.llm.storyteller.core.graph;

import nl.llm.storyteller.core.graph.model.EntityType;
import nl.llm.storyteller.core.graph.model.PredicateId;

public record PredicateDefinition(
  PredicateId id,
  EntityType subjectType,
  EntityType objectType,
  boolean temporal,
  String positiveText,
  String negativeText
) { }
