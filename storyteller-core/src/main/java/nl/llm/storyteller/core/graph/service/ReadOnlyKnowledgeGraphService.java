package nl.llm.storyteller.core.graph.service;

import nl.llm.storyteller.core.graph.KnowledgeGraphSnapshot;
import nl.llm.storyteller.core.graph.PredicateCatalog;
import nl.llm.storyteller.core.graph.PredicateDefinition;
import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphRepository;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Runtime facade for deterministic prompt grounding. */
public final class ReadOnlyKnowledgeGraphService implements KnowledgeGraphService {
  private final AtomicReference<KnowledgeGraphSnapshot> snapshot;
  private final KnowledgeGraphRepository store;
  private final PredicateCatalog predicates;

  public ReadOnlyKnowledgeGraphService(KnowledgeGraphRepository store) {
    this(store, PredicateCatalog.load(java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath()));
  }

  public ReadOnlyKnowledgeGraphService(KnowledgeGraphRepository store, PredicateCatalog predicates) {
    this.store = store;
    this.predicates = predicates;
    this.snapshot = new AtomicReference<>(store.loadSnapshot());
  }

  public ReadOnlyKnowledgeGraphService(KnowledgeGraphSnapshot snapshot) {
    this(snapshot, PredicateCatalog.load(java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath()));
  }

  public ReadOnlyKnowledgeGraphService(KnowledgeGraphSnapshot snapshot, PredicateCatalog predicates) {
    this.store = null;
    this.predicates = predicates;
    this.snapshot = new AtomicReference<>(snapshot);
  }

  public void publish(KnowledgeGraphSnapshot snapshot) {
    this.snapshot.set(snapshot);
  }

  @Override
  public KnowledgeGraphSnapshot current() {
    return snapshot.get();
  }

  @Override
  public KnowledgeGraphSnapshot refresh() {
    if (store != null) {
      snapshot.set(store.loadSnapshot());
    }
    return snapshot.get();
  }

  @Override
  public String relevantFacts(String text) {
    KnowledgeGraphSnapshot currentSnapshot = refreshIfAvailable();
    if (text == null || text.isBlank()) {
      return "";
    }

    Set<EntityId> mentioned = mentionedEntities(currentSnapshot, text);
    Set<Fact> relevant = new LinkedHashSet<>();
    for (EntityId entityId : mentioned) {
      currentSnapshot.factsBySubject(entityId).stream().filter(this::isGroundingFact).forEach(relevant::add);
      currentSnapshot.factsByObject(entityId).stream().filter(this::isGroundingFact).forEach(relevant::add);
    }
    if (relevant.isEmpty()) {
      return "";
    }

    String authoritative = relevant.stream()
      .filter(Fact::hard)
      .map(fact -> format(currentSnapshot, fact))
      .collect(Collectors.joining("\n"));
    String generated = relevant.stream()
      .filter(fact -> !fact.hard() && fact.source() == FactSource.TURNBASED)
      .map(fact -> format(currentSnapshot, fact))
      .collect(Collectors.joining("\n"));
    StringBuilder result = new StringBuilder();
    if (!authoritative.isBlank()) {
      result.append("Knowledge graph facts (authoritative; do not transfer traits between characters):\n")
        .append(authoritative);
    }
    if (!generated.isBlank()) {
      if (!result.isEmpty()) {
        result.append("\n");
      }
      result.append("Turn-based graph context (model-generated, lower confidence; never override authoritative facts):\n")
        .append(generated);
    }
    return result.toString();
  }

  private KnowledgeGraphSnapshot refreshIfAvailable() {
    if (store == null) {
      return snapshot.get();
    }
    try {
      refresh();
    } catch (RuntimeException _) {
      // Keep serving the last valid snapshot while a file edit is incomplete or invalid.
    }
    return snapshot.get();
  }

  private Set<EntityId> mentionedEntities(KnowledgeGraphSnapshot snapshot, String text) {
    String normalizedText = text.toLowerCase(Locale.ROOT);
    Set<EntityId> result = new LinkedHashSet<>();
    for (var entry : snapshot.entities().entrySet()) {
      Entity entity = entry.getValue();
      if (containsName(normalizedText, entity.name())
        || entity.aliases().stream().anyMatch(alias -> containsName(normalizedText, alias))) {
        result.add(entry.getKey());
      }
    }
    return result;
  }

  private boolean containsName(String normalizedText, String name) {
    if (name == null || name.isBlank()) {
      return false;
    }
    return Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(name.toLowerCase(Locale.ROOT))
      + "(?![\\p{L}\\p{N}_])").matcher(normalizedText).find();
  }

  private boolean isGroundingFact(Fact fact) {
    return fact.status() == FactStatus.ACTIVE
      && (fact.hard() || fact.source() == FactSource.TURNBASED);
  }

  private String format(KnowledgeGraphSnapshot snapshot, Fact fact) {
    String subject = snapshot.entity(fact.subject()).orElseThrow().name();
    String object = snapshot.entity(fact.object()).orElseThrow().name();
    PredicateDefinition definition = predicates.require(fact.predicate());
    String relation = fact.polarity() == Polarity.NEGATIVE
      ? definition.negativeText()
      : definition.positiveText();
    return "- " + subject + " " + relation + " " + object + ".";
  }
}
