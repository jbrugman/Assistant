package nl.llm.storyteller.cli.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkResultTest {
  @Test
  @DisplayName("""
    Given an external backend without a measurable process RSS,
    When the benchmark result is formatted,
    Then the meaningless peak server RSS line should be omitted
    """)
  void omitsUnavailableServerMemory() {
    BenchmarkResult result = new BenchmarkResult(
      new BenchmarkOptions("qwen3-vl-4b-instruct-mlx", 50, false, true, true),
      Duration.ofSeconds(10), 5, 5, 0, 0, null,
      0, 0, 0, 0, 0, 0, 0,
      Path.of("benchmark-results/result.md")
    );

    assertFalse(result.format().contains("Peak server RSS"));
    assertFalse(result.format().contains("external backend process"));
    assertFalse(result.format().contains("Generation:"));
    assertFalse(result.format().contains("tok/s"));
    assertTrue(result.format().contains("Validation retries:  0"));
    assertTrue(result.format().contains("Graph updates rejected: 0"));
  }
}
