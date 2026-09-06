package nl.llm.storyteller.cli.benchmark;

import nl.llm.storyteller.core.ApplicationContext;
import nl.llm.storyteller.core.TestAppConfigFactory;
import nl.llm.storyteller.core.config.AppConfigLoader;
import nl.llm.storyteller.core.service.PromptResourceLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
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
      assertTrue(validationPrompt.contains("Never return an empty response for REPLACE"));
      assertFalse(prompts.loadValidationRequestTemplate().isBlank());
      assertFalse(prompts.loadResetCacheBusterTemplate().isBlank());
    }
  }

  @Test
  @DisplayName("""
    Given custom validation prompts used by the normal application,
    When an isolated benchmark workspace is created,
    Then the benchmark should use those exact validation prompts
    """)
  void copiesNormalApplicationValidationPrompts() throws Exception {
    Path systemPrompt = tempDir.resolve("custom-validation-system.md");
    Path requestTemplate = tempDir.resolve("custom-validation-request.md");
    Files.writeString(systemPrompt, "Return a corrected REPLACE response directly.");
    Files.writeString(requestTemplate, "Validate exactly: %s %s %s %s");
    Path override = tempDir.resolve("test.config");
    Files.writeString(override, """
      file.validationSystemPrompt=%s
      file.validationRequestTemplate=%s
      """.formatted(systemPrompt, requestTemplate));
    var sourceConfig = AppConfigLoader.load(tempDir, override);
    var sourceContext = new ApplicationContext(
      sourceConfig, null, null, null, null, null, null, null, null, null
    );

    try (BenchmarkWorkspace workspace = BenchmarkWorkspace.create(
      sourceContext,
      BenchmarkOptions.parse("/benchmark --turns=10")
    )) {
      PromptResourceLoader prompts = new PromptResourceLoader(workspace.config());

      assertEquals("Return a corrected REPLACE response directly.", prompts.loadValidationSystemPrompt());
      assertEquals("Validate exactly: %s %s %s %s", prompts.loadValidationRequestTemplate());
    }
  }
}
