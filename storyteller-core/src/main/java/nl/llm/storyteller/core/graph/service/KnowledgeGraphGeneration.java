package nl.llm.storyteller.core.graph.service;

import java.io.IOException;

@FunctionalInterface
public interface KnowledgeGraphGeneration {
  KnowledgeGraphGenerator.GenerationResult generate(String fixedProtagonists) throws IOException, InterruptedException;
}
