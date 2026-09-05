package nl.llm.storyteller.core.graph.service;

import nl.llm.storyteller.core.TestAppConfigFactory;
import nl.llm.storyteller.core.service.DerivedMemoryTaskQueue;
import nl.llm.storyteller.core.service.PromptResourceLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeGraphFillServiceTest {
  @TempDir Path tempDir;

  @Test
  @DisplayName("""
    Given fixed protagonist data and a knowledge graph generator,
    When the knowledge graph fill service is executed,
    Then only the fixed protagonist data should be sent to the generator
    """)
  void sendsOnlyFixedProtagonistsToGenerator() throws Exception {
    Path prompts = tempDir.resolve("systemprompts");
    Files.createDirectories(prompts);
    String fixedProtagonists = "protagonist_profiles:\n  Valerie:\n    role: central_protagonist";
    Files.writeString(prompts.resolve("fixed_protagonists.yml"), fixedProtagonists);
    AtomicReference<String> received = new AtomicReference<>();
    var config = TestAppConfigFactory.load(tempDir);
    try (DerivedMemoryTaskQueue queue = new DerivedMemoryTaskQueue()) {
      var service = new KnowledgeGraphFillService(
        new PromptResourceLoader(config),
        input -> {
          received.set(input);
          return new KnowledgeGraphGenerator.GenerationResult(1, 0, 1);
        },
        queue
      );

      service.fill();
    }

    assertEquals(fixedProtagonists, received.get());
  }

  @Test
  @DisplayName("""
    Given an existing task on the shared derived-memory queue,
    When a knowledge graph fill is requested,
    Then generation should wait until the earlier task has completed
    """)
  void serializesFillWithExistingDerivedMemoryTasks() throws Exception {
    Path prompts = tempDir.resolve("systemprompts");
    Files.createDirectories(prompts);
    Files.writeString(prompts.resolve("fixed_protagonists.yml"), "fixed data");
    var config = TestAppConfigFactory.load(tempDir);
    CountDownLatch earlierTaskStarted = new CountDownLatch(1);
    CountDownLatch releaseEarlierTask = new CountDownLatch(1);
    CountDownLatch generationStarted = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();

    try (DerivedMemoryTaskQueue queue = new DerivedMemoryTaskQueue()) {
      queue.submit(() -> await(releaseEarlierTask, earlierTaskStarted));
      assertTrue(earlierTaskStarted.await(5, TimeUnit.SECONDS));
      var service = new KnowledgeGraphFillService(
        new PromptResourceLoader(config),
        input -> {
          generationStarted.countDown();
          return new KnowledgeGraphGenerator.GenerationResult(1, 0, 1);
        },
        queue
      );
      Thread fillThread = Thread.ofVirtual().start(() -> {
        try {
          service.fill();
        } catch (Throwable ex) {
          failure.set(ex);
        }
      });

      assertFalse(generationStarted.await(100, TimeUnit.MILLISECONDS));
      releaseEarlierTask.countDown();
      assertTrue(generationStarted.await(5, TimeUnit.SECONDS));
      fillThread.join();
    } finally {
      releaseEarlierTask.countDown();
    }

    assertNull(failure.get());
  }

  @Test
  @DisplayName("""
    Given knowledge graph generation fails inside the shared queue,
    When the fill service waits for its result,
    Then the original IOException should be propagated to the caller
    """)
  void preservesGenerationIOException() throws Exception {
    Path prompts = tempDir.resolve("systemprompts");
    Files.createDirectories(prompts);
    Files.writeString(prompts.resolve("fixed_protagonists.yml"), "fixed data");
    var config = TestAppConfigFactory.load(tempDir);
    var expected = new IOException("The model returned an invalid knowledge graph.");

    try (DerivedMemoryTaskQueue queue = new DerivedMemoryTaskQueue()) {
      var service = new KnowledgeGraphFillService(
        new PromptResourceLoader(config),
        input -> {
          throw expected;
        },
        queue
      );

      IOException actual = assertThrows(IOException.class, service::fill);

      assertSame(expected, actual);
    }
  }

  private void await(CountDownLatch release, CountDownLatch started) {
    started.countDown();
    try {
      release.await();
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }
}
