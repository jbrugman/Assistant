package nl.llm.storyteller.cli.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BenchmarkOptionsTest {
  @ParameterizedTest
  @CsvSource({
    "--validation=off, false, true, true",
    "--cache-buster=off, true, false, true",
    "--knowledge-graph=off, true, true, false"
  })
  @DisplayName("""
    Given a supported benchmark model and one disabled feature,
    When the command is parsed,
    Then only the requested feature should be disabled
    """)
  void parsesFeatureSwitches(String argument, boolean validation, boolean cacheBuster, boolean knowledgeGraph) {
    BenchmarkOptions options = BenchmarkOptions.parse(
      "/benchmark -qwen3-vl-4b-instruct-mlx " + argument
    );

    assertEquals(validation, options.validation());
    assertEquals(cacheBuster, options.cacheBuster());
    assertEquals(knowledgeGraph, options.knowledgeGraph());
  }

  @ParameterizedTest
  @CsvSource({
    "-qwen3-vl-4b-instruct-mlx, qwen3-vl-4b-instruct-mlx",
    "-gemma-4-26b-a4b-nl-vision-mlx, gemma-4-26b-a4b-nl-vision-mlx"
  })
  @DisplayName("""
    Given a model name as the first benchmark argument,
    When the command is parsed,
    Then that exact model should be selected for the benchmark
    """)
  void parsesRequestedModel(String argument, String expectedModel) {
    BenchmarkOptions options = BenchmarkOptions.parse("/benchmark " + argument);

    assertEquals(expectedModel, options.model());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "/benchmark",
    "/benchmark --validation=off"
  })
  @DisplayName("""
    Given no model argument,
    When the benchmark command is parsed,
    Then no model override should be sent to the already loaded backend model
    """)
  void usesLoadedBackendModelByDefault(String command) {
    BenchmarkOptions options = BenchmarkOptions.parse(command);

    assertEquals("", options.model());
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "/benchmark -",
    "/benchmark -qwen3-vl-4b-instruct-mlx --turns=9",
    "/benchmark -qwen3-vl-4b-instruct-mlx --validation=maybe",
    "/benchmark -qwen3-vl-4b-instruct-mlx --unknown=on"
  })
  @DisplayName("""
    Given an unsupported or malformed benchmark command,
    When the command is parsed,
    Then a clear argument error should be raised
    """)
  void rejectsInvalidCommands(String command) {
    assertThrows(IllegalArgumentException.class, () -> BenchmarkOptions.parse(command));
  }
}
