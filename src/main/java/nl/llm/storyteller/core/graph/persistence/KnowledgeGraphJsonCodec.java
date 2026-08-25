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
import nl.llm.storyteller.core.graph.model.Predicate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reflection-free JSON codec, including native-image builds. */
public final class KnowledgeGraphJsonCodec {
  public KnowledgeGraphDocument fromJson(String json) throws JsonProcessingException {
    JsonNode root = JsonSupport.OBJECT_MAPPER.readTree(json);
    Map<String, Entity> entities = new LinkedHashMap<>();
    root.path("entities").fields().forEachRemaining(entry -> {
      JsonNode node = entry.getValue();
      List<String> aliases = new ArrayList<>();
      node.path("aliases").forEach(alias -> aliases.add(alias.asText()));
      entities.put(entry.getKey(), new Entity(enumValue(EntityType.class, node, "type"), node.path("name").asText(), aliases));
    });

    List<Fact> facts = new ArrayList<>();
    root.path("facts").forEach(node -> facts.add(new Fact(
      node.path("id").asText(),
      entityId(node, "subject"),
      enumValue(Predicate.class, node, "predicate"),
      entityId(node, "object"),
      enumValue(Polarity.class, node, "polarity"),
      enumValue(FactStatus.class, node, "status"),
      enumValue(FactSource.class, node, "source"),
      node.path("sourceTurn").isIntegralNumber() ? node.path("sourceTurn").intValue() : null,
      node.path("hard").asBoolean(false)
    )));
    return new KnowledgeGraphDocument(root.path("schemaVersion").asInt(), root.path("revision").asLong(), entities, facts);
  }

  public ObjectNode toJson(KnowledgeGraphDocument document) {
    ObjectNode root = JsonSupport.OBJECT_MAPPER.createObjectNode();
    root.put("schemaVersion", document.schemaVersion());
    root.put("revision", document.revision());
    ObjectNode entities = root.putObject("entities");
    document.entities().forEach((id, entity) -> {
      ObjectNode node = entities.putObject(id);
      putEnum(node, "type", entity.type());
      node.put("name", entity.name());
      ArrayNode aliases = node.putArray("aliases");
      entity.aliases().forEach(aliases::add);
    });
    ArrayNode facts = root.putArray("facts");
    document.facts().forEach(fact -> {
      ObjectNode node = facts.addObject();
      node.put("id", fact.id());
      putEntityId(node, "subject", fact.subject());
      putEnum(node, "predicate", fact.predicate());
      putEntityId(node, "object", fact.object());
      putEnum(node, "polarity", fact.polarity());
      putEnum(node, "status", fact.status());
      putEnum(node, "source", fact.source());
      if (fact.sourceTurn() == null) node.putNull("sourceTurn"); else node.put("sourceTurn", fact.sourceTurn());
      node.put("hard", fact.hard());
    });
    return root;
  }

  private EntityId entityId(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isTextual() ? new EntityId(value.asText()) : null;
  }

  private <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node, String field) {
    String value = node.path(field).asText("");
    if (value.isBlank()) return null;
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException ex) {
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
