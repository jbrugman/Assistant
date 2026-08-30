package nl.llm.storyteller.core.graph.turnbasedservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import nl.llm.storyteller.core.graph.PredicateCatalog;
import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactKey;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphJsonCodec;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphStore;
import nl.llm.storyteller.core.graph.service.ReadOnlyKnowledgeGraphService;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.service.ChatClient;
import nl.llm.storyteller.core.service.DerivedMemoryTaskQueue;
import nl.llm.storyteller.core.service.HistoryStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TurnBasedKnowledgeGraphService {
  private final HistoryStore historyStore;
  private final ChatClient chatClient;
  private final KnowledgeGraphStore store;
  private final ReadOnlyKnowledgeGraphService graphService;
  private final PredicateCatalog predicates;
  private final DerivedMemoryTaskQueue taskQueue;
  private final int batchTurns;
  private final Map<String, Object> options;
  private final int timeoutSeconds;
  private final KnowledgeGraphJsonCodec codec = new KnowledgeGraphJsonCodec();

  public TurnBasedKnowledgeGraphService(
    HistoryStore historyStore,
    ChatClient chatClient,
    KnowledgeGraphStore store,
    ReadOnlyKnowledgeGraphService graphService,
    PredicateCatalog predicates,
    DerivedMemoryTaskQueue taskQueue,
    int batchTurns,
    Map<String, Object> options,
    int timeoutSeconds
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
  }

  public void startUpdateIfNeeded() {
    List<Message> messages = historyStore.load().messages();
    int completedTurns = messages.size() / 2;
    if (completedTurns == 0 || completedTurns % batchTurns != 0) {
      return;
    }

    int firstMessage = Math.max(0, messages.size() - (batchTurns * 2));
    List<Message> batch = List.copyOf(messages.subList(firstMessage, messages.size()));
    taskQueue.submit(() -> updateFromTurns(batch, completedTurns));
  }

  void updateFromTurns(List<Message> turns, int latestTurn) {
    try {
      if (!batchStillPresent(turns, latestTurn)) {
        return;
      }
      KnowledgeGraphDocument current = store.load();
      long startingRevision = current.revision();
      String response = chatClient.chat(
        List.of(
          new Message("system", systemPrompt()),
          new Message("user", userPrompt(current, turns))
        ),
        options,
        timeoutSeconds
      );
      KnowledgeGraphDocument candidate = parse(response);
      if (!batchStillPresent(turns, latestTurn)) {
        return;
      }
      store.update(existing -> existing.revision() == startingRevision
        ? merge(existing, candidate, latestTurn)
        : existing);
      graphService.publish(store.loadSnapshot());
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    } catch (IOException | RuntimeException _) {
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
    candidate.entities().forEach((id, entity) -> {
      Entity existing = entities.get(id);
      if (existing == null || existing.source() == FactSource.TURNBASED) {
        entities.put(id, new Entity(entity.type(), entity.name(), entity.aliases(), FactSource.TURNBASED));
      }
    });

    List<Fact> facts = new ArrayList<>(current.facts());
    for (Fact candidateFact : candidate.facts()) {
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
    String json = response == null ? "" : response.trim();
    if (json.startsWith("```")) {
      int firstNewline = json.indexOf('\n');
      int closingFence = json.lastIndexOf("```");
      if (firstNewline >= 0 && closingFence > firstNewline) {
        json = json.substring(firstNewline + 1, closingFence).trim();
      }
    }
    try {
      return codec.fromJson(json);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("The model returned an invalid turn-based knowledge graph", ex);
    }
  }

  private String systemPrompt() {
    return """
      Extract only explicit knowledge-graph entities and facts from the supplied completed story turns.
      Return JSON only with schemaVersion, revision, entities, and facts.
      Reuse stable entity and fact IDs from the current graph when applicable.
      Every entity and fact must use source TURNBASED. Facts must use status ACTIVE, hard false,
      and one of these configured directional predicates: %s.
      Do not guess, infer uncertain information, or repeat unrelated facts from the current graph.
      Turn-based data is generated context with lower authority than manual or fixed-protagonist data.
      """.formatted(predicates.modelInstructions());
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
