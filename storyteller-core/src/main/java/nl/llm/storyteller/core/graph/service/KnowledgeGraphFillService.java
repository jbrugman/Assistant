package nl.llm.storyteller.core.graph.service;

import nl.llm.storyteller.core.service.PromptResourceLoader;

import java.io.IOException;

public final class KnowledgeGraphFillService {
  private final PromptResourceLoader promptResourceLoader;
  private final KnowledgeGraphGeneration generator;

  public KnowledgeGraphFillService(
    PromptResourceLoader promptResourceLoader,
    KnowledgeGraphGeneration generator
  ) {
    this.promptResourceLoader = promptResourceLoader;
    this.generator = generator;
  }

  public KnowledgeGraphGenerator.GenerationResult fill() throws IOException, InterruptedException {
    return generator.generate(promptResourceLoader.loadFixedProtagonists());
  }
}
