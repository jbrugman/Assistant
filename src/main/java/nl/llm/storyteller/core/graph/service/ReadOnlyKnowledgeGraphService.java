package nl.llm.storyteller.core.graph.service;

import nl.llm.storyteller.core.graph.KnowledgeGraphSnapshot;
import nl.llm.storyteller.core.graph.PredicateCatalog;
import nl.llm.storyteller.core.graph.PredicateDefinition;
import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphStore;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Runtime facade for deterministic prompt grounding. */
public final class ReadOnlyKnowledgeGraphService implements KnowledgeGraphService {
  private volatile KnowledgeGraphSnapshot snapshot;
  private final KnowledgeGraphStore store;
  private final PredicateCatalog predicates;

  public ReadOnlyKnowledgeGraphService(KnowledgeGraphStore store) {
    this(store, PredicateCatalog.load(java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath()));
  }

  public ReadOnlyKnowledgeGraphService(KnowledgeGraphStore store, PredicateCatalog predicates) {
    this.store = store;
    this.predicates = predicates;
    this.snapshot = store.loadSnapshot();
  }

  public ReadOnlyKnowledgeGraphService(KnowledgeGraphSnapshot snapshot) {
    this(snapshot, PredicateCatalog.load(java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath()));
  }

  public ReadOnlyKnowledgeGraphService(KnowledgeGraphSnapshot snapshot, PredicateCatalog predicates) {
    this.store = null;
    this.predicates = predicates;
    this.snapshot = snapshot;
  }

  public void publish(KnowledgeGraphSnapshot snapshot) {
    this.snapshot = snapshot;
  }

  @Override
  public KnowledgeGraphSnapshot current() {
    return snapshot;
  }

  @Override
  public KnowledgeGraphSnapshot refresh() {
    if (store != null) {
      snapshot = store.loadSnapshot();
    }
    return snapshot;
  }

  @Override
  public String relevantFacts(String text) {
    refreshIfAvailable();
    if (text == null || text.isBlank()) {
      return "";
    }

    Set<EntityId> mentioned = mentionedEntities(text);
    Set<Fact> relevant = new LinkedHashSet<>();
    for (EntityId entityId : mentioned) {
      snapshot.factsBySubject(entityId).stream().filter(this::isGroundingFact).forEach(relevant::add);
      snapshot.factsByObject(entityId).stream().filter(this::isGroundingFact).forEach(relevant::add);
    }
    if (relevant.isEmpty()) {
      return "";
    }

    String authoritative = relevant.stream()
      .filter(Fact::hard)
      .map(this::format)
      .collect(Collectors.joining("\n"));
    String generated = relevant.stream()
      .filter(fact -> !fact.hard() && fact.source() == FactSource.TURNBASED)
      .map(this::format)
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

  private void refreshIfAvailable() {
    if (store == null) {
      return;
    }
    try {
      refresh();
    } catch (RuntimeException _) {
      // Keep serving the last valid snapshot while a file edit is incomplete or invalid.
    }
  }

  private Set<EntityId> mentionedEntities(String text) {
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

  private String format(Fact fact) {
    String subject = snapshot.entity(fact.subject()).orElseThrow().name();
    String object = snapshot.entity(fact.object()).orElseThrow().name();
    PredicateDefinition definition = predicates.require(fact.predicate());
    String relation = fact.polarity() == Polarity.NEGATIVE
      ? definition.negativeText()
      : definition.positiveText();
    return "- " + subject + " " + relation + " " + object + ".";
  }
}
