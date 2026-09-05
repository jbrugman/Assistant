package nl.llm.storyteller.core;

import nl.llm.storyteller.core.service.DerivedMemoryTaskQueue;
import nl.llm.storyteller.core.service.ManagedLlamaServer;
import nl.llm.storyteller.core.service.ManagedMlxServer;
import nl.llm.storyteller.core.service.StoryExportService;
import nl.llm.storyteller.core.service.StorySessionService;
import nl.llm.storyteller.core.graph.service.KnowledgeGraphFillService;
import nl.llm.storyteller.core.graph.service.KnowledgeGraphInitializer;
import nl.llm.storyteller.core.graph.service.KnowledgeGraphManagementService;
import nl.llm.storyteller.core.graph.service.KnowledgeGraphService;

public record ApplicationContext(
  nl.llm.storyteller.core.config.AppConfig config,
  DerivedMemoryTaskQueue derivedMemoryTaskQueue,
  StorySessionService storySessionService,
  StoryExportService storyExportService,
  KnowledgeGraphService knowledgeGraphService,
  KnowledgeGraphInitializer knowledgeGraphInitializer,
  KnowledgeGraphFillService knowledgeGraphFillService,
  KnowledgeGraphManagementService knowledgeGraphManagementService,
  ManagedLlamaServer managedLlamaServer,
  ManagedMlxServer managedMlxServer
) implements AutoCloseable {
  @Override
  public void close() {
    derivedMemoryTaskQueue.close();
    if (managedLlamaServer != null) {
      managedLlamaServer.close();
    }
    if (managedMlxServer != null) {
      managedMlxServer.close();
    }
  }
}
