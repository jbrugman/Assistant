package nl.llm.storyteller.core.graph.service;

import nl.llm.storyteller.core.service.PromptResourceLoader;
import nl.llm.storyteller.core.service.DerivedMemoryTaskQueue;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public final class KnowledgeGraphFillService {
  private final PromptResourceLoader promptResourceLoader;
  private final KnowledgeGraphGeneration generator;
  private final DerivedMemoryTaskQueue taskQueue;

  public KnowledgeGraphFillService(
    PromptResourceLoader promptResourceLoader,
    KnowledgeGraphGeneration generator,
    DerivedMemoryTaskQueue taskQueue
  ) {
    this.promptResourceLoader = promptResourceLoader;
    this.generator = generator;
    this.taskQueue = taskQueue;
  }

  public KnowledgeGraphGenerator.GenerationResult fill() throws IOException, InterruptedException {
    String fixedProtagonists = promptResourceLoader.loadFixedProtagonists();
    try {
      return taskQueue.submitAndWait(() -> generator.generate(fixedProtagonists));
    } catch (ExecutionException ex) {
      return rethrowCause(ex.getCause());
    }
  }

  private KnowledgeGraphGenerator.GenerationResult rethrowCause(Throwable cause)
    throws IOException, InterruptedException {
    if (cause instanceof IOException ioException) {
      throw ioException;
    }
    if (cause instanceof InterruptedException interruptedException) {
      throw interruptedException;
    }
    if (cause instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    if (cause instanceof Error error) {
      throw error;
    }
    throw new IOException("Knowledge graph generation failed.", cause);
  }
}
