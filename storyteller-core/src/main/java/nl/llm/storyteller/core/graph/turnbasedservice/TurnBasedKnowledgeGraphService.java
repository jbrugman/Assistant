package nl.llm.storyteller.core.graph.turnbasedservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import nl.llm.storyteller.core.graph.KnowledgeGraphJsonResponse;
import nl.llm.storyteller.core.graph.PredicateCatalog;
import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactKey;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.model.PredicateId;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphJsonCodec;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphRepository;
import nl.llm.storyteller.core.graph.service.ReadOnlyKnowledgeGraphService;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.service.ChatClient;
import nl.llm.storyteller.core.service.DerivedMemoryTaskQueue;
import nl.llm.storyteller.core.service.StoryHistory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class TurnBasedKnowledgeGraphService {
  private static final PredicateId WEARS = new PredicateId("WEARS");
  private static final Set<PredicateId> NON_TURN_BASED_PREDICATES = Set.of(
    new PredicateId("LIVES"), new PredicateId("LIVES_WITH")
  );

  private final StoryHistory historyStore;
  private final ChatClient chatClient;
  private final KnowledgeGraphRepository store;
  private final ReadOnlyKnowledgeGraphService graphService;
  private final PredicateCatalog predicates;
  private final DerivedMemoryTaskQueue taskQueue;
  private final int batchTurns;
  private final Map<String, Object> options;
  private final int timeoutSeconds;
  private final KnowledgeGraphJsonCodec codec = new KnowledgeGraphJsonCodec();
  private final KnowledgeGraphUpdateObserver observer;

  public TurnBasedKnowledgeGraphService(
    StoryHistory historyStore,
    ChatClient chatClient,
    KnowledgeGraphRepository store,
    ReadOnlyKnowledgeGraphService graphService,
    PredicateCatalog predicates,
    DerivedMemoryTaskQueue taskQueue,
    int batchTurns,
    Map<String, Object> options,
    int timeoutSeconds
  ) {
    this(
      historyStore, chatClient, store, graphService, predicates, taskQueue, batchTurns, options,
      timeoutSeconds, KnowledgeGraphUpdateObserver.NONE
    );
  }

  public TurnBasedKnowledgeGraphService(
    StoryHistory historyStore,
    ChatClient chatClient,
    KnowledgeGraphRepository store,
    ReadOnlyKnowledgeGraphService graphService,
    PredicateCatalog predicates,
    DerivedMemoryTaskQueue taskQueue,
    int batchTurns,
    Map<String, Object> options,
    int timeoutSeconds,
    KnowledgeGraphUpdateObserver observer
  ) {
    this.historyStore = historyStore;
    this.chatClient = chatClient;
    this.store = store;
    this.graphService = graphService;
    this.predicates = predicates;
    this.taskQueue = taskQueue;
    this.batchTurns = batchTurns;
    this.options = options;
    this.timeoutSeconds = timeoutSeconds;
    this.observer = observer;
  }

  public void startUpdateIfNeeded() {
    List<Message> messages = historyStore.load().messages();
    int completedTurns = messages.size() / 2;
    int lastProcessedTurn = store.load().facts().stream()
      .filter(fact -> fact.source() == FactSource.TURNBASED)
      .map(Fact::sourceTurn)
      .filter(Objects::nonNull)
      .mapToInt(Integer::intValue)
      .max()
      .orElse(0);
    if (completedTurns - lastProcessedTurn < batchTurns) {
      return;
    }

    int firstMessage = Math.max(0, messages.size() - (batchTurns * 2));
    List<Message> batch = List.copyOf(messages.subList(firstMessage, messages.size()));
    taskQueue.submit(() -> updateFromTurns(batch, completedTurns));
  }

  void updateFromTurns(List<Message> turns, int latestTurn) {
    String rawResponse = "";
    try {
      if (!batchStillPresent(turns, latestTurn)) {
        return;
      }
      KnowledgeGraphDocument current = store.load();
      long startingRevision = current.revision();
      rawResponse = chatClient.chat(
        List.of(
          new Message("system", systemPrompt()),
          new Message("user", userPrompt(current, turns))
        ),
        options,
        timeoutSeconds
      );
      KnowledgeGraphDocument candidate = parse(rawResponse);
      if (!batchStillPresent(turns, latestTurn)) {
        return;
      }
      AtomicBoolean applied = new AtomicBoolean(false);
      KnowledgeGraphDocument updated = store.update(existing -> {
        if (existing.revision() != startingRevision) {
          return existing;
        }
        applied.set(true);
        return merge(existing, candidate, latestTurn);
      });
      if (!applied.get()) {
        observer.skipped(latestTurn, startingRevision, updated.revision());
        return;
      }
      graphService.publish(store.loadSnapshot());
      observer.succeeded(latestTurn, updated.revision(), updated.entities().size(), updated.facts().size());
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    } catch (IOException | RuntimeException ex) {
      String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
      observer.failed(latestTurn, reason + responseSnippet(rawResponse));
      // Turn-derived graph updates are best-effort and must never fail the completed story turn.
    }
  }

  private boolean batchStillPresent(List<Message> turns, int latestTurn) {
    List<Message> currentMessages = historyStore.load().messages();
    int lastMessage = latestTurn * 2;
    int firstMessage = lastMessage - turns.size();
    return firstMessage >= 0
      && lastMessage <= currentMessages.size()
      && currentMessages.subList(firstMessage, lastMessage).equals(turns);
  }

  KnowledgeGraphDocument merge(
    KnowledgeGraphDocument current,
    KnowledgeGraphDocument candidate,
    int latestTurn
  ) {
    Map<String, Entity> entities = new LinkedHashMap<>(current.entities());
    List<Fact> candidateFacts = candidate.facts().stream()
      .filter(fact -> !NON_TURN_BASED_PREDICATES.contains(fact.predicate()))
      .toList();
    Set<EntityId> candidateReferences = candidateFacts.stream()
      .flatMap(fact -> java.util.stream.Stream.of(fact.subject(), fact.object()))
      .collect(Collectors.toSet());
    candidate.entities().forEach((id, entity) -> {
      Entity existing = entities.get(id);
      if (candidateReferences.contains(new EntityId(id))
        && (existing == null || existing.source() == FactSource.TURNBASED)) {
        entities.put(id, new Entity(entity.type(), entity.name(), entity.aliases(), FactSource.TURNBASED));
      }
    });

    List<Fact> facts = new ArrayList<>(current.facts());
    Set<EntityId> removedResidenceEntities = facts.stream()
      .filter(fact -> fact.source() == FactSource.TURNBASED)
      .filter(fact -> NON_TURN_BASED_PREDICATES.contains(fact.predicate()))
      .flatMap(fact -> java.util.stream.Stream.of(fact.subject(), fact.object()))
      .collect(Collectors.toSet());
    facts.removeIf(fact -> fact.source() == FactSource.TURNBASED
      && NON_TURN_BASED_PREDICATES.contains(fact.predicate()));

    Set<EntityId> refreshedWearers = candidateFacts.stream()
      .filter(fact -> WEARS.equals(fact.predicate()) && fact.polarity() == Polarity.POSITIVE)
      .map(Fact::subject)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<EntityId> replacedGarments = facts.stream()
      .filter(fact -> fact.source() == FactSource.TURNBASED)
      .filter(fact -> WEARS.equals(fact.predicate()) && refreshedWearers.contains(fact.subject()))
      .map(Fact::object)
      .collect(Collectors.toCollection(LinkedHashSet::new));
    facts.removeIf(fact -> fact.source() == FactSource.TURNBASED
      && WEARS.equals(fact.predicate())
      && refreshedWearers.contains(fact.subject()));

    for (Fact candidateFact : candidateFacts) {
      Fact normalized = new Fact(
        candidateFact.id(),
        candidateFact.subject(),
        candidateFact.predicate(),
        candidateFact.object(),
        candidateFact.polarity(),
        FactStatus.ACTIVE,
        FactSource.TURNBASED,
        latestTurn,
        false
      );
      FactKey key = new FactKey(normalized.subject(), normalized.predicate(), normalized.object());
      boolean protectedFactExists = facts.stream().anyMatch(fact ->
        fact.source() != FactSource.TURNBASED
          && (fact.id().equals(normalized.id()) || sameKey(fact, key))
      );
      if (protectedFactExists) {
        continue;
      }
      facts.removeIf(fact -> fact.source() == FactSource.TURNBASED
        && (fact.id().equals(normalized.id()) || sameKey(fact, key)));
      facts.add(normalized);
    }

    replacedGarments.forEach(garment -> {
      Entity entity = entities.get(garment.value());
      boolean stillReferenced = facts.stream().anyMatch(fact ->
        garment.equals(fact.subject()) || garment.equals(fact.object()));
      if (entity != null && entity.source() == FactSource.TURNBASED && !stillReferenced) {
        entities.remove(garment.value());
      }
    });
    removedResidenceEntities.forEach(entityId -> {
      Entity entity = entities.get(entityId.value());
      boolean stillReferenced = facts.stream().anyMatch(fact ->
        entityId.equals(fact.subject()) || entityId.equals(fact.object()));
      if (entity != null && entity.source() == FactSource.TURNBASED && !stillReferenced) {
        entities.remove(entityId.value());
      }
    });

    return new KnowledgeGraphDocument(
      KnowledgeGraphDocument.CURRENT_SCHEMA_VERSION,
      current.revision() + 1,
      entities,
      facts
    );
  }

  private boolean sameKey(Fact fact, FactKey key) {
    return fact.subject() != null
      && fact.predicate() != null
      && fact.object() != null
      && new FactKey(fact.subject(), fact.predicate(), fact.object()).equals(key);
  }

  private KnowledgeGraphDocument parse(String response) {
    try {
      return codec.fromJson(KnowledgeGraphJsonResponse.extract(response));
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("The model returned an invalid turn-based knowledge graph", ex);
    }
  }

  private String systemPrompt() {
    return """
      Extract only explicit knowledge-graph entities and facts from the supplied completed story turns.
      Return JSON only. Use exactly this object shape and field names:
      {
        "schemaVersion": 1,
        "revision": 0,
        "entities": {
          "character.alice": {
            "type": "CHARACTER",
            "name": "Alice",
            "aliases": [],
            "source": "TURNBASED"
          },
          "item.compass": {
            "type": "ITEM",
            "name": "compass",
            "aliases": [],
            "source": "TURNBASED"
          }
        },
        "facts": [
          {
            "id": "fact.alice_possesses_compass",
            "subject": "character.alice",
            "predicate": "POSSESSES",
            "object": "item.compass",
            "polarity": "POSITIVE",
            "status": "ACTIVE",
            "source": "TURNBASED",
            "sourceTurn": null,
            "hard": false
          }
        ]
      }
      The example only demonstrates the schema. Do not copy Alice or the compass unless the supplied turns support them.
      `entities` must be a JSON object keyed by entity ID, never an array and never a name-to-type map.
      Entity and fact IDs must be lowercase identifiers matching [a-z][a-z0-9]*(?:[._-][a-z0-9]+)*.
      Every entity requires type, name, aliases, and source. Every fact requires id, subject, predicate,
      object, polarity, status, source, sourceTurn, and hard. References must use IDs present in `entities`
      or already present in the current graph.
      Reuse stable entity and fact IDs from the current graph when applicable.
      Every entity and fact must use source TURNBASED. Facts must use status ACTIVE, hard false,
      and one of these configured directional predicates: %s.
      Do not guess, infer uncertain information, or repeat unrelated facts from the current graph.
      Treat interactions as events, not proof of an enduring interpersonal relationship. Talking,
      flirting, kissing, having sex, cooperating, spending time together, or showing momentary
      affection does not by itself establish LOVES, FRIENDS_WITH, TRUSTS, FEELS_SAFE_WITH,
      PROTECTIVE_OF, or another relationship predicate. Emit such a relationship only when the
      supplied turns explicitly establish that relationship as a fact. When in doubt, omit it.
      Never emit LIVES or LIVES_WITH from story turns. Current location, travel, passing through a
      place, visiting, staying somewhere temporarily, and co-presence belong in canonical state,
      not in the knowledge graph. Residence facts are maintained only as fixed or manual graph data.
      Represent clothing with WEARS from a CHARACTER to an ITEM. Create one ITEM entity and one
      WEARS fact per distinct garment or outfit description; never put an array or multiple garments
      in a single fact object. When the supplied turns change a character's clothing, return the
      character's complete resulting outfit as WEARS facts, including unchanged garments that remain
      worn. Omission from that resulting set means a previous TURNBASED garment is no longer worn.
      Turn-based data is generated context with lower authority than manual or fixed-protagonist data.
      """.formatted(predicates.modelInstructionsExcluding(NON_TURN_BASED_PREDICATES));
  }

  private String responseSnippet(String response) {
    if (response == null || response.isBlank()) {
      return "";
    }
    String normalized = response.trim().replaceAll("\\s+", " ");
    return "; response: " + normalized.substring(0, Math.min(normalized.length(), 1_000));
  }

  private String userPrompt(KnowledgeGraphDocument current, List<Message> turns) {
    StringBuilder prompt = new StringBuilder("Current graph for ID reference:\n")
      .append(codec.toJson(current).toPrettyString())
      .append("\n\nCompleted turns to extract:\n");
    for (Message message : turns) {
      prompt.append(message.role().toUpperCase()).append(": ").append(message.content()).append('\n');
    }
    return prompt.toString().trim();
  }
}
