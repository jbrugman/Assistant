package nl.llm.storyteller.core.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class LlmBackendGuard {
  private final String name;
  private final int failureThreshold;
  private final int cooldownSeconds;
  private final Clock clock;

  private int consecutiveFailures;
  private Instant openUntil = Instant.EPOCH;

  public LlmBackendGuard(String name, int failureThreshold, int cooldownSeconds) {
    this(name, failureThreshold, cooldownSeconds, Clock.systemUTC());
  }

  LlmBackendGuard(String name, int failureThreshold, int cooldownSeconds, Clock clock) {
    this.name = Objects.requireNonNull(name);
    this.failureThreshold = failureThreshold;
    this.cooldownSeconds = cooldownSeconds;
    this.clock = Objects.requireNonNull(clock);
  }

  <T> T execute(InterruptibleSupplier<T> supplier) throws IOException, InterruptedException {
    ensureAvailable();

    try {
      T result = supplier.get();
      recordSuccess();
      return result;
    } catch (IOException | RuntimeException ex) {
      recordFailure();
      throw ex;
    }
  }

  synchronized boolean isOpen() {
    return Instant.now(clock).isBefore(openUntil);
  }

  synchronized int consecutiveFailures() {
    return consecutiveFailures;
  }

  private synchronized void ensureAvailable() throws IOException {
    if (isOpen()) {
      throw new IOException(name + " is temporarily in cooldown after repeated failures. Try again shortly.");
    }
  }

  private synchronized void recordSuccess() {
    consecutiveFailures = 0;
    openUntil = Instant.EPOCH;
  }

  private synchronized void recordFailure() {
    consecutiveFailures++;
    if (consecutiveFailures >= failureThreshold) {
      openUntil = Instant.now(clock).plusSeconds(cooldownSeconds);
    }
  }
}
