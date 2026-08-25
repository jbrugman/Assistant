package nl.llm.storyteller.core.graph;

import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphStore;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Minimal read-only runtime facade for deterministic prompt grounding. */
public final class ReadOnlyKnowledgeGraphService implements KnowledgeGraphService {
  private volatile KnowledgeGraphSnapshot snapshot;
  private final KnowledgeGraphStore store;

  public ReadOnlyKnowledgeGraphService(KnowledgeGraphStore store) {
    this.store = store;
    this.snapshot = store.loadSnapshot();
  }

  public ReadOnlyKnowledgeGraphService(KnowledgeGraphSnapshot snapshot) {
    this.store = null;
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

    return "Knowledge graph facts (authoritative; do not transfer traits between characters):\n"
      + relevant.stream().map(this::format).collect(java.util.stream.Collectors.joining("\n"));
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
    return fact.hard() && fact.status() == FactStatus.ACTIVE;
  }

  private String format(Fact fact) {
    String subject = snapshot.entity(fact.subject()).orElseThrow().name();
    String object = snapshot.entity(fact.object()).orElseThrow().name();
    String relation = switch (fact.predicate()) {
      case POSSESSES -> fact.polarity() == Polarity.NEGATIVE ? "does not possess" : "possesses";
      case CAN_PERFORM -> fact.polarity() == Polarity.NEGATIVE ? "cannot perform" : "can perform";
      case LOVES -> fact.polarity() == Polarity.NEGATIVE ? "does not love" : "loves";
    };
    return "- " + subject + " " + relation + " " + object + ".";
  }
}
