package nl.llm.storyteller.core;

import nl.llm.storyteller.core.service.DerivedMemoryTaskQueue;
import nl.llm.storyteller.core.service.StoryExportService;
import nl.llm.storyteller.core.service.StorySessionService;

public record ApplicationContext(
  AppConfig config,
  DerivedMemoryTaskQueue derivedMemoryTaskQueue,
  StorySessionService storySessionService,
  StoryExportService storyExportService
) implements AutoCloseable {
  @Override
  public void close() {
    derivedMemoryTaskQueue.close();
  }
}
