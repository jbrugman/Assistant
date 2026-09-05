package nl.llm.storyteller.core.service;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DerivedMemoryTaskQueue implements AutoCloseable {
  private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
    Thread thread = new Thread(runnable, "derived-memory-worker");
    thread.setDaemon(true);
    return thread;
  });

  public void submit(Runnable task) {
    executor.submit(task);
  }

  public <T> T submitAndWait(Callable<T> task) throws InterruptedException, ExecutionException {
    return executor.submit(task).get();
  }

  public boolean awaitIdle(long timeout, TimeUnit unit) throws InterruptedException {
    CountDownLatch marker = new CountDownLatch(1);
    executor.submit(marker::countDown);
    return marker.await(timeout, unit);
  }

  @Override
  public void close() {
    executor.shutdownNow();
  }
}
