package nl.llm.storyteller.core.graph;

import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.EntityType;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.model.Predicate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeGraphValidatorTest {
  private final KnowledgeGraphValidator validator = new KnowledgeGraphValidator();

  @Test
  void acceptsLoveBetweenCharacters() {
    EntityId valerie = new EntityId("character.valerie");
    EntityId mike = new EntityId("character.mike");
    KnowledgeGraphDocument document = new KnowledgeGraphDocument(
      KnowledgeGraphDocument.CURRENT_SCHEMA_VERSION,
      1,
      Map.of(
        valerie.value(), new Entity(EntityType.CHARACTER, "Valerie", List.of()),
        mike.value(), new Entity(EntityType.CHARACTER, "Mike", List.of())
      ),
      List.of(new Fact("valerie-loves-mike", valerie, Predicate.LOVES, mike, Polarity.POSITIVE,
        FactStatus.ACTIVE, FactSource.MANUAL, null, true))
    );

    validator.validate(document);
  }

  @Test
  void acceptsClosedOntologyFactWithMatchingEntityTypes() {
    assertDoesNotThrow(() -> validator.validate(validDocument()));
  }

  @Test
  void rejectsUnknownReferencesAndWrongPredicateTypes() {
    KnowledgeGraphDocument invalid = new KnowledgeGraphDocument(
      1,
      0,
      Map.of(
        "character.valerie", new Entity(EntityType.CHARACTER, "Valerie", List.of()),
        "skill.singing", new Entity(EntityType.SKILL, "Singing", List.of())
      ),
      List.of(
        fact("fact.wrong_type", Predicate.POSSESSES, "character.valerie", "skill.singing", Polarity.POSITIVE),
        fact("fact.unknown", Predicate.CAN_PERFORM, "character.missing", "skill.singing", Polarity.POSITIVE)
      )
    );

    KnowledgeGraphValidationException exception = assertThrows(
      KnowledgeGraphValidationException.class,
      () -> validator.validate(invalid)
    );

    assertTrue(exception.violations().stream().anyMatch(message -> message.contains("expected CHARACTER -> ITEM")));
    assertTrue(exception.violations().stream().anyMatch(message -> message.contains("unknown subject")));
  }

  @Test
  void rejectsDuplicateAndContradictoryActiveFacts() {
    KnowledgeGraphDocument valid = validDocument();
    List<Fact> facts = new ArrayList<>(valid.facts());
    facts.add(fact(
      "fact.valerie_microphone_negative",
      Predicate.POSSESSES,
      "character.valerie",
      "item.microphone",
      Polarity.NEGATIVE
    ));

    KnowledgeGraphValidationException exception = assertThrows(
      KnowledgeGraphValidationException.class,
      () -> validator.validate(new KnowledgeGraphDocument(1, 0, valid.entities(), facts))
    );

    assertTrue(exception.violations().stream().anyMatch(message -> message.contains("contradictory active facts")));
  }

  @Test
  void rejectsUnsupportedSchemaAndInvalidIdentifiers() {
    KnowledgeGraphDocument invalid = new KnowledgeGraphDocument(
      2,
      -1,
      Map.of("Valerie!", new Entity(EntityType.CHARACTER, "Valerie", List.of())),
      List.of()
    );

    KnowledgeGraphValidationException exception = assertThrows(
      KnowledgeGraphValidationException.class,
      () -> validator.validate(invalid)
    );

    assertTrue(exception.violations().stream().anyMatch(message -> message.contains("unsupported schemaVersion")));
    assertTrue(exception.violations().stream().anyMatch(message -> message.contains("revision must not be negative")));
    assertTrue(exception.violations().stream().anyMatch(message -> message.contains("invalid entity id")));
  }

  private KnowledgeGraphDocument validDocument() {
    return new KnowledgeGraphDocument(
      1,
      0,
      Map.of(
        "character.valerie", new Entity(EntityType.CHARACTER, "Valerie", List.of()),
        "item.microphone", new Entity(EntityType.ITEM, "Microphone", List.of())
      ),
      List.of(fact(
        "fact.valerie_microphone",
        Predicate.POSSESSES,
        "character.valerie",
        "item.microphone",
        Polarity.POSITIVE
      ))
    );
  }

  private Fact fact(String id, Predicate predicate, String subject, String object, Polarity polarity) {
    return new Fact(
      id,
      new EntityId(subject),
      predicate,
      new EntityId(object),
      polarity,
      FactStatus.ACTIVE,
      FactSource.MANUAL,
      null,
      true
    );
  }
}
