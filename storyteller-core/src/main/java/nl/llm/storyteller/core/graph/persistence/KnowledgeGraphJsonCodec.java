package nl.llm.storyteller.core.graph.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nl.llm.storyteller.core.JsonSupport;
import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.EntityType;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.model.PredicateId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reflection-free JSON codec, including native-image builds. */
public final class KnowledgeGraphJsonCodec {
  private static final String ALIASES = "aliases";
  private static final String ENTITIES = "entities";
  private static final String FACTS = "facts";
  private static final String HARD = "hard";
  private static final String ID = "id";
  private static final String NAME = "name";
  private static final String OBJECT = "object";
  private static final String POLARITY = "polarity";
  private static final String PREDICATE = "predicate";
  private static final String REVISION = "revision";
  private static final String SCHEMA_VERSION = "schemaVersion";
  private static final String SOURCE = "source";
  private static final String SOURCE_TURN = "sourceTurn";
  private static final String STATUS = "status";
  private static final String SUBJECT = "subject";
  private static final String TYPE = "type";

  public KnowledgeGraphDocument fromJson(String json) throws JsonProcessingException {
    JsonNode root = JsonSupport.OBJECT_MAPPER.readTree(json);
    Map<String, Entity> entities = new LinkedHashMap<>();
    root.path(ENTITIES).properties().forEach(entry -> {
      JsonNode node = entry.getValue();
      List<String> aliases = new ArrayList<>();
      node.path(ALIASES).forEach(alias -> aliases.add(alias.asText()));
      FactSource source = enumValue(FactSource.class, node, SOURCE);
      entities.put(entry.getKey(), new Entity(
        enumValue(EntityType.class, node, TYPE),
        node.path(NAME).asText(),
        aliases,
        source == null ? FactSource.MANUAL : source
      ));
    });

    List<Fact> facts = new ArrayList<>();
    root.path(FACTS).forEach(node -> facts.add(new Fact(
      node.path(ID).asText(),
      entityId(node, SUBJECT),
      predicateId(node),
      entityId(node, OBJECT),
      enumValue(Polarity.class, node, POLARITY),
      enumValue(FactStatus.class, node, STATUS),
      enumValue(FactSource.class, node, SOURCE),
      node.path(SOURCE_TURN).isIntegralNumber() ? node.path(SOURCE_TURN).intValue() : null,
      node.path(HARD).asBoolean(false)
    )));
    return new KnowledgeGraphDocument(
      root.path(SCHEMA_VERSION).asInt(), root.path(REVISION).asLong(), entities, facts
    );
  }

  public ObjectNode toJson(KnowledgeGraphDocument document) {
    ObjectNode root = JsonSupport.OBJECT_MAPPER.createObjectNode();
    root.put(SCHEMA_VERSION, document.schemaVersion());
    root.put(REVISION, document.revision());
    ObjectNode entities = root.putObject(ENTITIES);
    document.entities().forEach((id, entity) -> {
      ObjectNode node = entities.putObject(id);
      putEnum(node, TYPE, entity.type());
      node.put(NAME, entity.name());
      ArrayNode aliases = node.putArray(ALIASES);
      entity.aliases().forEach(aliases::add);
      putEnum(node, SOURCE, entity.source());
    });
    ArrayNode facts = root.putArray(FACTS);
    document.facts().forEach(fact -> {
      ObjectNode node = facts.addObject();
      node.put(ID, fact.id());
      putEntityId(node, SUBJECT, fact.subject());
      if (fact.predicate() == null) node.putNull(PREDICATE); else node.put(PREDICATE, fact.predicate().value());
      putEntityId(node, OBJECT, fact.object());
      putEnum(node, POLARITY, fact.polarity());
      putEnum(node, STATUS, fact.status());
      putEnum(node, SOURCE, fact.source());
      if (fact.sourceTurn() == null) node.putNull(SOURCE_TURN); else node.put(SOURCE_TURN, fact.sourceTurn());
      node.put(HARD, fact.hard());
    });
    return root;
  }

  private EntityId entityId(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isTextual() ? new EntityId(value.asText()) : null;
  }

  private PredicateId predicateId(JsonNode node) {
    JsonNode value = node.path(KnowledgeGraphJsonCodec.PREDICATE);
    return value.isTextual() ? new PredicateId(value.asText()) : null;
  }

  private <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node, String field) {
    String value = node.path(field).asText("");
    if (value.isBlank()) return null;
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException _) {
      return null;
    }
  }

  private void putEntityId(ObjectNode node, String field, EntityId id) {
    if (id == null) node.putNull(field); else node.put(field, id.value());
  }

  private void putEnum(ObjectNode node, String field, Enum<?> value) {
    if (value == null) node.putNull(field); else node.put(field, value.name());
  }
}
