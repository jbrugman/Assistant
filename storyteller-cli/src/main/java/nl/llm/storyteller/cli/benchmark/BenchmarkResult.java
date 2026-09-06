package nl.llm.storyteller.cli.benchmark;

import java.time.Duration;
import java.nio.file.Path;

public record BenchmarkResult(
  BenchmarkOptions options,
  Duration duration,
  int probes,
  int passedProbes,
  int entityErrors,
  int stateErrors,
  Long peakServerMemoryBytes,
  int validationRequests,
  int validationReplacements,
  int validationImprovements,
  int validationRegressions,
  int validationProbes,
  int passedValidationProbes,
  int graphUpdateFailures,
  Path auditReport
) {
  public double factsRetainedPercentage() {
    return probes == 0 ? 0.0 : 100.0 * passedProbes / probes;
  }

  public String format() {
    return """
      Assistant Benchmark
      ────────────────────────────────────
      Model:           %s
      Turns:           %d
      Temperature:     0
      Top-k / top-p:   1 / 1
      Context turns:   2
      KV context:      4096
      Seed:            42
      Validation:      %s
      Cache-buster:    %s
      Knowledge graph: %s

      Total time:      %s
      Avg turn:        %.2fs

      Validator requests:  %d
      Validation replacements: %d
      Validation improvements: %d
      Validation regressions:  %d
      Validation probes passed: %d/%d
      Validation retries:  %d
      Probes passed:   %d/%d
      Entity errors:   %d
      State errors:    %d
      Facts retained:  %.1f%%
      Graph updates rejected: %d
      %sAudit report:    %s
      """.formatted(
      BenchmarkRunner.displayModel(options), options.turns(), onOff(options.validation()), onOff(options.cacheBuster()),
      onOff(options.knowledgeGraph()), formatDuration(duration), duration.toMillis() / 1000.0 / options.turns(),
      validationRequests, validationReplacements, validationImprovements, validationRegressions,
      passedValidationProbes, validationProbes, validationRetries(), passedProbes, probes,
      entityErrors, stateErrors, factsRetainedPercentage(),
      graphUpdateFailures, memoryLine(), auditReport
    );
  }

  private int validationRetries() {
    return options.validation() ? Math.max(0, validationRequests - options.turns()) : 0;
  }

  private String memoryLine() {
    if (peakServerMemoryBytes == null) {
      return "";
    }
    return "Peak server RSS: %.1f GB unified memory%n".formatted(peakServerMemoryBytes / 1_000_000_000.0);
  }

  private static String onOff(boolean enabled) {
    return enabled ? "on" : "off";
  }

  private static String formatDuration(Duration duration) {
    long seconds = duration.toSeconds();
    return "%dm %02ds".formatted(seconds / 60, seconds % 60);
  }
}
