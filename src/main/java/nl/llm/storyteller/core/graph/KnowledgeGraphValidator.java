package nl.llm.storyteller.core.graph;

import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactKey;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Polarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class KnowledgeGraphValidator {
  private static final String ENTITY_PREFIX = "entity '";
  private static final String FACT_PREFIX = "fact '";
  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-z][a-z0-9]*+(?:[._-][a-z0-9]++)*+");

  public void validate(KnowledgeGraphDocument document) {
    List<String> violations = violations(document);
    if (!violations.isEmpty()) {
      throw new KnowledgeGraphValidationException(violations);
    }
  }

  public List<String> violations(KnowledgeGraphDocument document) {
    if (document == null) {
      return List.of("document is required");
    }

    List<String> violations = new ArrayList<>();
    validateMetadata(document, violations);
    Map<EntityId, Entity> entities = validateEntities(document, violations);
    validateFacts(document, entities, violations);
    return List.copyOf(violations);
  }

  private void validateMetadata(KnowledgeGraphDocument document, List<String> violations) {
    if (document.schemaVersion() != KnowledgeGraphDocument.CURRENT_SCHEMA_VERSION) {
      violations.add("unsupported schemaVersion " + document.schemaVersion());
    }
    if (document.revision() < 0) {
      violations.add("revision must not be negative");
    }
  }

  private Map<EntityId, Entity> validateEntities(
    KnowledgeGraphDocument document,
    List<String> violations
  ) {
    Map<EntityId, Entity> entities = new HashMap<>();
    for (Map.Entry<String, Entity> entry : document.entities().entrySet()) {
      String rawId = entry.getKey() == null ? "" : entry.getKey().trim();
      EntityId id = new EntityId(rawId);
      Entity entity = entry.getValue();
      if (!IDENTIFIER_PATTERN.matcher(rawId).matches()) {
        violations.add("invalid entity id '" + rawId + "'");
      }
      if (entity == null) {
        violations.add(ENTITY_PREFIX + rawId + "' is required");
        continue;
      }
      if (entity.type() == null) {
        violations.add(ENTITY_PREFIX + rawId + "' has no type");
      }
      if (entity.name().isBlank()) {
        violations.add(ENTITY_PREFIX + rawId + "' has no name");
      }
      validateAliases(id, entity, violations);
      entities.put(id, entity);
    }
    return entities;
  }

  private void validateAliases(EntityId id, Entity entity, List<String> violations) {
    Set<String> normalizedAliases = new HashSet<>();
    for (String alias : entity.aliases()) {
      if (alias.isBlank()) {
        violations.add(ENTITY_PREFIX + id + "' contains a blank alias");
      } else if (!normalizedAliases.add(KnowledgeGraphSnapshot.normalizeAlias(alias))) {
        violations.add(ENTITY_PREFIX + id + "' contains duplicate alias '" + alias + "'");
      }
    }
  }

  private void validateFacts(
    KnowledgeGraphDocument document,
    Map<EntityId, Entity> entities,
    List<String> violations
  ) {
    Set<String> factIds = new HashSet<>();
    Map<FactKey, Polarity> activeFacts = new HashMap<>();
    for (int index = 0; index < document.facts().size(); index++) {
      Fact fact = document.facts().get(index);
      if (fact == null) {
        violations.add("fact at index " + index + " is required");
        continue;
      }
      validateFactFields(fact, factIds, violations);
      Entity subject = entities.get(fact.subject());
      Entity object = entities.get(fact.object());
      if (fact.subject() != null && subject == null) {
        violations.add(FACT_PREFIX + fact.id() + "' references unknown subject '" + fact.subject() + "'");
      }
      if (fact.object() != null && object == null) {
        violations.add(FACT_PREFIX + fact.id() + "' references unknown object '" + fact.object() + "'");
      }
      validatePredicateTypes(fact, subject, object, violations);
      validateActiveFact(fact, activeFacts, violations);
    }
  }

  private void validateFactFields(Fact fact, Set<String> factIds, List<String> violations) {
    if (!IDENTIFIER_PATTERN.matcher(fact.id()).matches()) {
      violations.add("invalid fact id '" + fact.id() + "'");
    } else if (!factIds.add(fact.id())) {
      violations.add("duplicate fact id '" + fact.id() + "'");
    }
    if (fact.subject() == null || fact.subject().value().isBlank()) {
      violations.add(FACT_PREFIX + fact.id() + "' has no subject");
    }
    if (fact.predicate() == null) {
      violations.add(FACT_PREFIX + fact.id() + "' has no predicate");
    }
    if (fact.object() == null || fact.object().value().isBlank()) {
      violations.add(FACT_PREFIX + fact.id() + "' has no object");
    }
    if (fact.polarity() == null) {
      violations.add(FACT_PREFIX + fact.id() + "' has no polarity");
    }
    if (fact.status() == null) {
      violations.add(FACT_PREFIX + fact.id() + "' has no status");
    }
    if (fact.source() == null) {
      violations.add(FACT_PREFIX + fact.id() + "' has no source");
    }
    if (fact.sourceTurn() != null && fact.sourceTurn() < 0) {
      violations.add(FACT_PREFIX + fact.id() + "' has a negative sourceTurn");
    }
  }

  private void validatePredicateTypes(
    Fact fact,
    Entity subject,
    Entity object,
    List<String> violations
  ) {
    if (fact.predicate() == null || subject == null || object == null
      || subject.type() == null || object.type() == null) {
      return;
    }
    if (subject.type() != fact.predicate().subjectType() || object.type() != fact.predicate().objectType()) {
      violations.add(
        FACT_PREFIX + fact.id() + "' uses " + fact.predicate() + " with "
          + subject.type() + " -> " + object.type() + ", expected "
          + fact.predicate().subjectType() + " -> " + fact.predicate().objectType()
      );
    }
  }

  private void validateActiveFact(
    Fact fact,
    Map<FactKey, Polarity> activeFacts,
    List<String> violations
  ) {
    if (fact.status() != FactStatus.ACTIVE || fact.subject() == null
      || fact.predicate() == null || fact.object() == null || fact.polarity() == null) {
      return;
    }

    FactKey key = new FactKey(fact.subject(), fact.predicate(), fact.object());
    Polarity previous = activeFacts.putIfAbsent(key, fact.polarity());
    if (previous == fact.polarity()) {
      violations.add("duplicate active fact for " + key);
    } else if (previous != null) {
      violations.add("contradictory active facts for " + key);
    }
  }
}
