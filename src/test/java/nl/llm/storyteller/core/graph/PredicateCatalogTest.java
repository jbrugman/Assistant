package nl.llm.storyteller.core.graph;

import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.EntityType;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.model.PredicateId;
import nl.llm.storyteller.core.graph.service.ReadOnlyKnowledgeGraphService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PredicateCatalogTest {
  @Test
  @DisplayName("""
    Given the default predicate catalog configuration,
    When the residence predicates are loaded,
    Then their configured types and text should be available
    """)
  void defaultCatalogContainsConfiguredResidencePredicates() {
    PredicateCatalog catalog = PredicateCatalog.load(Path.of(System.getProperty("user.dir")).toAbsolutePath());

    assertEquals(EntityType.CHARACTER, catalog.require(new PredicateId("LIVES_WITH")).objectType());
    assertEquals(EntityType.LOCATION, catalog.require(new PredicateId("LIVES")).objectType());
    assertEquals("lives at", catalog.require(new PredicateId("LIVES")).positiveText());
  }

  @Test
  @DisplayName("""
    Given a predicate configured in JSON without a corresponding Java enum,
    When the predicate is validated and rendered,
    Then it should work without a Java code change
    """)
  void supportsConfiguredPredicateWithoutJavaEnumChange() {
    PredicateCatalog catalog = PredicateCatalog.fromJson("""
      {"predicates":{"MENTORS":{
        "subjectType":"CHARACTER",
        "objectType":"CHARACTER",
        "temporal":false,
        "positiveText":"mentors",
        "negativeText":"does not mentor"
      }}}
      """);
    EntityId valerie = new EntityId("character.valerie");
    EntityId chris = new EntityId("character.chris");
    KnowledgeGraphDocument document = new KnowledgeGraphDocument(1, 1, Map.of(
      valerie.value(), new Entity(EntityType.CHARACTER, "Valerie", List.of()),
      chris.value(), new Entity(EntityType.CHARACTER, "Chris", List.of())
    ), List.of(new Fact("valerie-mentors-chris", valerie, new PredicateId("MENTORS"), chris,
      Polarity.POSITIVE, FactStatus.ACTIVE, FactSource.MANUAL, null, true)));
    KnowledgeGraphValidator validator = new KnowledgeGraphValidator(catalog);
    ReadOnlyKnowledgeGraphService service = new ReadOnlyKnowledgeGraphService(
      KnowledgeGraphSnapshot.from(document, validator), catalog
    );

    validator.validate(document);
    assertTrue(service.relevantFacts("Valerie arrives.").contains("Valerie mentors Chris."));
    assertTrue(catalog.modelInstructions().contains("MENTORS (CHARACTER to CHARACTER)"));
  }
}
