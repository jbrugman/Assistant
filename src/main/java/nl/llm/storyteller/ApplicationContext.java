package nl.llm.storyteller;

import nl.llm.storyteller.service.DerivedMemoryTaskQueue;
import nl.llm.storyteller.service.StoryExportService;
import nl.llm.storyteller.service.StorySessionService;

record ApplicationContext(
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
