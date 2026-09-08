package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.TestAppConfigFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptResourceLoaderTest {
  @ParameterizedTest
  @MethodSource("sessionPrompts")
  @DisplayName("""
    Given story prompts stored for a session,
    When an editable story prompt is loaded,
    Then the session value should be used instead of the configured file
    """)
  void shouldLoadEditableStoryPromptsFromSession(
    String expected,
    Function<PromptResourceLoader, String> loader
  ) {
    var config = TestAppConfigFactory.load(Path.of("."));
    var resources = new PromptResourceLoader(
      config,
      () -> new StoryPrompts("DATABASE SYSTEM", "DATABASE PROTAGONISTS", "DATABASE RULES")
    );

    String actual = loader.apply(resources);

    assertEquals(expected, actual);
  }

  private static Stream<Arguments> sessionPrompts() {
    return Stream.of(
      Arguments.of("DATABASE SYSTEM", (Function<PromptResourceLoader, String>) PromptResourceLoader::loadSystemPrompt),
      Arguments.of("DATABASE PROTAGONISTS", (Function<PromptResourceLoader, String>) PromptResourceLoader::loadFixedProtagonists),
      Arguments.of("DATABASE RULES", (Function<PromptResourceLoader, String>) PromptResourceLoader::loadRulesPrompt)
    );
  }
}
