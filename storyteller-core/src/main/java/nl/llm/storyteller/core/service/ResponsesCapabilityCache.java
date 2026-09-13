package nl.llm.storyteller.core.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

final class ResponsesCapabilityCache {
  enum Support { UNKNOWN, SUPPORTED, UNSUPPORTED }

  private final ConcurrentMap<String, AtomicReference<Support>> supportByEndpoint = new ConcurrentHashMap<>();

  AtomicReference<Support> forEndpoint(String endpointUrl) {
    return supportByEndpoint.computeIfAbsent(endpointUrl, _ -> new AtomicReference<>(Support.UNKNOWN));
  }
}
