package nl.llm.storyteller.core.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DerivedMemoryTaskQueue implements AutoCloseable {
  private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
    Thread thread = new Thread(runnable, "derived-memory-worker");
    thread.setDaemon(true);
    return thread;
  });

  void submit(Runnable task) {
    executor.submit(task);
  }

  @Override
  public void close() {
    executor.shutdownNow();
  }
}
