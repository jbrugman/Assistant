package nl.llm.storyteller.core.graph;

import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactKey;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.model.TruthValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class KnowledgeGraphSnapshot {
  private final long revision;
  private final Map<EntityId, Entity> entitiesById;
  private final Map<String, Set<EntityId>> entitiesByAlias;
  private final Map<EntityId, List<Fact>> factsBySubject;
  private final Map<EntityId, List<Fact>> factsByObject;
  private final Map<FactKey, TruthValue> truthByKey;

  private KnowledgeGraphSnapshot(
    long revision,
    Map<EntityId, Entity> entitiesById,
    Map<String, Set<EntityId>> entitiesByAlias,
    Map<EntityId, List<Fact>> factsBySubject,
    Map<EntityId, List<Fact>> factsByObject,
    Map<FactKey, TruthValue> truthByKey
  ) {
    this.revision = revision;
    this.entitiesById = Collections.unmodifiableMap(new LinkedHashMap<>(entitiesById));
    this.entitiesByAlias = immutableSetMap(entitiesByAlias);
    this.factsBySubject = immutableListMap(factsBySubject);
    this.factsByObject = immutableListMap(factsByObject);
    this.truthByKey = Collections.unmodifiableMap(new LinkedHashMap<>(truthByKey));
  }

  public static KnowledgeGraphSnapshot from(
    KnowledgeGraphDocument document,
    KnowledgeGraphValidator validator
  ) {
    validator.validate(document);

    Map<EntityId, Entity> entitiesById = new LinkedHashMap<>();
    Map<String, Set<EntityId>> entitiesByAlias = new LinkedHashMap<>();
    for (Map.Entry<String, Entity> entry : document.entities().entrySet()) {
      EntityId entityId = new EntityId(entry.getKey());
      Entity entity = entry.getValue();
      entitiesById.put(entityId, entity);
      indexAlias(entitiesByAlias, entity.name(), entityId);
      for (String alias : entity.aliases()) {
        indexAlias(entitiesByAlias, alias, entityId);
      }
    }

    Map<EntityId, List<Fact>> factsBySubject = new LinkedHashMap<>();
    Map<EntityId, List<Fact>> factsByObject = new LinkedHashMap<>();
    Map<FactKey, TruthValue> truthByKey = new LinkedHashMap<>();
    for (Fact fact : document.facts()) {
      factsBySubject.computeIfAbsent(fact.subject(), ignored -> new ArrayList<>()).add(fact);
      factsByObject.computeIfAbsent(fact.object(), ignored -> new ArrayList<>()).add(fact);
      if (fact.status() == FactStatus.ACTIVE) {
        TruthValue truthValue = fact.polarity() == Polarity.POSITIVE ? TruthValue.TRUE : TruthValue.FALSE;
        truthByKey.put(new FactKey(fact.subject(), fact.predicate(), fact.object()), truthValue);
      }
    }

    return new KnowledgeGraphSnapshot(
      document.revision(), entitiesById, entitiesByAlias, factsBySubject, factsByObject, truthByKey
    );
  }

  public long revision() {
    return revision;
  }

  public Map<EntityId, Entity> entities() {
    return entitiesById;
  }

  public Optional<Entity> entity(EntityId id) {
    return Optional.ofNullable(entitiesById.get(id));
  }

  public Set<EntityId> resolveAlias(String nameOrAlias) {
    return entitiesByAlias.getOrDefault(normalizeAlias(nameOrAlias), Set.of());
  }

  public List<Fact> factsBySubject(EntityId subject) {
    return factsBySubject.getOrDefault(subject, List.of());
  }

  public List<Fact> factsByObject(EntityId object) {
    return factsByObject.getOrDefault(object, List.of());
  }

  public TruthValue truthValue(FactKey key) {
    return truthByKey.getOrDefault(key, TruthValue.UNKNOWN);
  }

  static String normalizeAlias(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private static void indexAlias(Map<String, Set<EntityId>> index, String alias, EntityId entityId) {
    index.computeIfAbsent(normalizeAlias(alias), ignored -> new LinkedHashSet<>()).add(entityId);
  }

  private static <K, V> Map<K, List<V>> immutableListMap(Map<K, List<V>> source) {
    Map<K, List<V>> result = new LinkedHashMap<>();
    source.forEach((key, value) -> result.put(key, List.copyOf(value)));
    return Collections.unmodifiableMap(result);
  }

  private static <K, V> Map<K, Set<V>> immutableSetMap(Map<K, Set<V>> source) {
    Map<K, Set<V>> result = new LinkedHashMap<>();
    source.forEach((key, value) -> result.put(key, Set.copyOf(value)));
    return Collections.unmodifiableMap(result);
  }
}
