package nl.llm.storyteller.cli.benchmark;

import nl.llm.storyteller.core.ApplicationContext;
import nl.llm.storyteller.core.TestAppConfigFactory;
import nl.llm.storyteller.core.service.PromptResourceLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkWorkspaceTest {
  @TempDir
  Path tempDir;

  @Test
  @DisplayName("""
    Given a normal configuration whose external prompt files do not exist,
    When an isolated benchmark workspace is created,
    Then every benchmark prompt should be available without using those external files
    """)
  void providesEveryPromptInsideTheIsolatedWorkspace() throws Exception {
    var sourceConfig = TestAppConfigFactory.load(tempDir);
    var sourceContext = new ApplicationContext(
      sourceConfig, null, null, null, null, null, null, null, null, null
    );
    var options = BenchmarkOptions.parse("/benchmark -gemma-4-26b-a4b-nl-vision-mlx --turns=10");

    try (BenchmarkWorkspace workspace = BenchmarkWorkspace.create(sourceContext, options)) {
      PromptResourceLoader prompts = new PromptResourceLoader(workspace.config());

      assertEquals("gemma-4-26b-a4b-nl-vision-mlx", workspace.config().chatModel());
      assertEquals("gemma-4-26b-a4b-nl-vision-mlx", workspace.config().validatorModel());
      String storySystemPrompt = prompts.loadSystemPrompt();
      assertFalse(storySystemPrompt.isBlank());
      assertFalse(storySystemPrompt.contains("Paris, green"));
      assertFalse(prompts.loadRulesPrompt().isBlank());
      assertFalse(prompts.loadFixedProtagonistsContextTemplate().isBlank());
      assertFalse(prompts.loadSummarySystemPrompt().isBlank());
      assertFalse(prompts.loadRecentSummarySystemPrompt().isBlank());
      assertFalse(prompts.loadCanonicalStateSystemPrompt().isBlank());
      String validationPrompt = prompts.loadValidationSystemPrompt();
      assertFalse(validationPrompt.isBlank());
      assertFalse(validationPrompt.contains("Paris, green"));
      assertTrue(validationPrompt.contains("final rules checker"));
      assertTrue(validationPrompt.contains("decision\":\"REPLACE"));
      assertFalse(prompts.loadValidationRequestTemplate().isBlank());
      assertFalse(prompts.loadResetCacheBusterTemplate().isBlank());
    }
  }
}
