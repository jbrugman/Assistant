package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.TestAppConfigFactory;
import nl.llm.storyteller.core.graph.KnowledgeGraphGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeGraphFillServiceTest {
  @TempDir Path tempDir;

  @Test
  void sendsOnlyFixedProtagonistsToGenerator() throws Exception {
    Path prompts = tempDir.resolve("systemprompts");
    Files.createDirectories(prompts);
    String fixedProtagonists = "protagonist_profiles:\n  Valerie:\n    role: central_protagonist";
    Files.writeString(prompts.resolve("fixed_protagonists.yml"), fixedProtagonists);
    AtomicReference<String> received = new AtomicReference<>();
    var config = TestAppConfigFactory.load(tempDir);
    var service = new KnowledgeGraphFillService(
      new PromptResourceLoader(config),
      input -> {
        received.set(input);
        return new KnowledgeGraphGenerator.GenerationResult(1, 0, 1);
      }
    );

    service.fill();

    assertEquals(fixedProtagonists, received.get());
  }
}
