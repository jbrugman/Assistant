package nl.llm.storyteller.cli.benchmark;

import nl.llm.storyteller.core.service.ChatRequestMetrics;
import nl.llm.storyteller.core.service.StoryTurnObserver;
import nl.llm.storyteller.core.graph.turnbasedservice.KnowledgeGraphUpdateObserver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class BenchmarkMetrics implements ChatRequestMetrics, StoryTurnObserver, KnowledgeGraphUpdateObserver {
  private int validationRequests;
  private TurnResponses lastTurn;
  private final List<String> graphFailures = new ArrayList<>();

  @Override
  public synchronized void recordRequest(String purpose, long tokens, Duration duration) {
    if ("validation".equals(purpose)) {
      validationRequests++;
    }
  }

  synchronized int validationRequests() {
    return validationRequests;
  }

  @Override
  public synchronized void completed(String userInput, String draftResponse, String finalResponse) {
    lastTurn = new TurnResponses(draftResponse, finalResponse);
  }

  synchronized TurnResponses lastTurn() {
    if (lastTurn == null) {
      throw new IllegalStateException("No completed benchmark turn was observed.");
    }
    return lastTurn;
  }

  @Override
  public synchronized void failed(int latestTurn, String reason) {
    graphFailures.add("Turn " + latestTurn + ": " + reason);
  }

  synchronized List<String> graphFailures() {
    return List.copyOf(graphFailures);
  }

  record TurnResponses(String draftResponse, String finalResponse) {
  }
}
