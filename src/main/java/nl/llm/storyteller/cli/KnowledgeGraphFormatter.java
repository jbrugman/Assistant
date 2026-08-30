package nl.llm.storyteller.cli;

import nl.llm.storyteller.core.graph.KnowledgeGraphSnapshot;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.Polarity;

import java.nio.file.Path;
import java.util.Comparator;

final class KnowledgeGraphFormatter {
  private KnowledgeGraphFormatter() {
  }

  static String format(KnowledgeGraphSnapshot graph, Path graphFile) {
    StringBuilder result = new StringBuilder("Knowledge graph: ").append(graphFile.toAbsolutePath().normalize());
    if (graph.entities().isEmpty()) {
      return result.append("\n\nThe graph is empty. Add entities and facts to this JSON file; Storyteller loads changes automatically.")
        .toString();
    }

    result.append("\nRevision: ").append(graph.revision()).append("\n\nEntities:");
    graph.entities().entrySet().stream()
      .sorted(Comparator.comparing(entry -> entry.getKey().value()))
      .forEach(entry -> result.append("\n- ").append(entry.getValue().name())
        .append(" [").append(entry.getValue().type()).append("]")
        .append(" source=").append(entry.getValue().source())
        .append(entry.getValue().aliases().isEmpty() ? "" : " aliases=" + entry.getValue().aliases()));

    result.append("\n\nFacts:");
    graph.entities().keySet().stream()
      .flatMap(subject -> graph.factsBySubject(subject).stream())
      .filter(fact -> fact.status() == FactStatus.ACTIVE)
      .sorted(Comparator.comparing(Fact::id))
      .forEach(fact -> result.append("\n- ").append(formatFact(graph, fact)));
    return result.toString();
  }

  private static String formatFact(KnowledgeGraphSnapshot graph, Fact fact) {
    String subject = graph.entity(fact.subject()).orElseThrow().name();
    String object = graph.entity(fact.object()).orElseThrow().name();
    String polarity = fact.polarity() == Polarity.NEGATIVE ? "NOT " : "";
    return subject + " " + polarity + fact.predicate() + " " + object
      + " [source=" + fact.source() + (fact.hard() ? ", hard" : "") + "]";
  }
}
