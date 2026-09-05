package nl.llm.storyteller.core.service;

import java.time.Duration;

@FunctionalInterface
public interface ChatRequestMetrics {
  ChatRequestMetrics NONE = (purpose, completionTokens, duration) -> { };

  void recordRequest(String purpose, long completionTokens, Duration duration);
}
