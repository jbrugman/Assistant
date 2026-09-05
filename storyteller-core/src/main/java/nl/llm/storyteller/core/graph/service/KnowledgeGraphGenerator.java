package nl.llm.storyteller.core.graph.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import nl.llm.storyteller.core.graph.KnowledgeGraphJsonResponse;
import nl.llm.storyteller.core.graph.PredicateCatalog;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphStore;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphJsonCodec;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.service.ChatClient;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.stream.Collectors;
import nl.llm.storyteller.core.graph.model.EntityType;
import nl.llm.storyteller.core.graph.model.Entity;

public final class KnowledgeGraphGenerator implements KnowledgeGraphGeneration {
  private final ChatClient chatClient;
  private final KnowledgeGraphStore store;
  private final ReadOnlyKnowledgeGraphService graphService;
  private final Map<String, Object> options;
  private final int timeoutSeconds;
  private final KnowledgeGraphJsonCodec codec = new KnowledgeGraphJsonCodec();
  private final PredicateCatalog predicates;

  public KnowledgeGraphGenerator(
    ChatClient chatClient,
    KnowledgeGraphStore store,
    ReadOnlyKnowledgeGraphService graphService,
    Map<String, Object> options,
    int timeoutSeconds
  ) {
    this(chatClient, store, graphService, options, timeoutSeconds,
      PredicateCatalog.load(java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath()));
  }

  public KnowledgeGraphGenerator(
    ChatClient chatClient,
    KnowledgeGraphStore store,
    ReadOnlyKnowledgeGraphService graphService,
    Map<String, Object> options,
    int timeoutSeconds,
    PredicateCatalog predicates
  ) {
    this.chatClient = chatClient;
    this.store = store;
    this.graphService = graphService;
    this.options = options;
    this.timeoutSeconds = timeoutSeconds;
    this.predicates = predicates;
  }

  @Override
  public GenerationResult generate(String storyContext) throws IOException, InterruptedException {
    String response = chatClient.chat(List.of(
      new Message("system", systemPrompt()),
      new Message("user", storyContext)
    ), options, timeoutSeconds);
    KnowledgeGraphDocument document = normalize(parse(response));
    store.save(document);
    graphService.publish(store.loadSnapshot());
    return new GenerationResult(document.entities().size(), document.facts().size(), document.revision());
  }

  private KnowledgeGraphDocument normalize(KnowledgeGraphDocument candidate) {
    Map<String, Entity> entities = new LinkedHashMap<>();
    candidate.entities().forEach((id, entity) -> entities.put(id, new Entity(
      entity.type(),
      entity.name(),
      entity.aliases(),
      FactSource.FIXED_PROTAGONIST
    )));
    List<Fact> facts = candidate.facts().stream()
      .map(fact -> new Fact(
        fact.id(),
        fact.subject(),
        fact.predicate(),
        fact.object(),
        fact.polarity(),
        FactStatus.ACTIVE,
        FactSource.FIXED_PROTAGONIST,
        null,
        true
      ))
      .toList();
    return new KnowledgeGraphDocument(
      KnowledgeGraphDocument.CURRENT_SCHEMA_VERSION,
      graphService.current().revision() + 1,
      entities,
      facts
    );
  }

  private KnowledgeGraphDocument parse(String response) {
    try {
      return codec.fromJson(KnowledgeGraphJsonResponse.extract(response));
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("The model returned an invalid knowledge graph: " + ex.getOriginalMessage(), ex);
    }
  }

  public record GenerationResult(int entities, int facts, long revision) { }

  private String systemPrompt() {
    return """
    Extract a knowledge graph from the supplied story context. Return JSON only, without commentary.
    The root fields are schemaVersion, revision, entities, and facts.
    Entities is an object keyed by stable lowercase IDs. Each entity has one of these types: %s, and has
    a name, aliases, and source FIXED_PROTAGONIST. Facts may only use these configured predicates: %s.
    Predicates are directional. Include explicit negative facts only when the source explicitly rules them out.
    Each fact needs a unique id, subject, predicate, object, polarity
    (POSITIVE or NEGATIVE), status ACTIVE, source FIXED_PROTAGONIST, sourceTurn null, and hard true.
    Include only facts explicitly supported by the context. Do not guess. Do not emit contradictory facts.
    """.formatted(
      Arrays.stream(EntityType.values()).map(Enum::name).collect(Collectors.joining(", ")),
      predicates.modelInstructions()
    );
  }
}
