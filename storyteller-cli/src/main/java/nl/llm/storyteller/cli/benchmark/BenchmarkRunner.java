package nl.llm.storyteller.cli.benchmark;

import nl.llm.storyteller.core.ApplicationContext;
import nl.llm.storyteller.core.ApplicationFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class BenchmarkRunner implements BenchmarkExecutor {
  private static final Pattern BENCHMARK_KV_LIMIT = Pattern.compile("(?:^|\\s)--max-kv-size\\s+4096(?:\\s|$)");
  private static final Pattern MAX_OUTPUT_TOKENS = Pattern.compile("(?:^|\\s)--max-tokens\\s+(\\d+)(?:\\s|$)");

  @Override
  public BenchmarkResult run(ApplicationContext sourceContext, BenchmarkOptions options)
    throws IOException, InterruptedException {
    verifyManagedModel(sourceContext, options);
    Instant started = Instant.now();
    BenchmarkMetrics metrics = new BenchmarkMetrics();
    BenchmarkRunState state = executeBenchmark(sourceContext, options, metrics);
    List<String> graphFailures = metrics.graphFailures();
    Path auditReport = writeAuditReport(sourceContext, options, state.probeResults, graphFailures);

    return new BenchmarkResult(
      options, Duration.between(started, Instant.now()), state.probes, state.passed, state.entityErrors, state.stateErrors,
      state.peakServerMemoryBytes, metrics.validationRequests(), state.validationReplacements, state.validationImprovements,
      state.validationRegressions, state.validationProbes, state.passedValidationProbes,
      graphFailures.size(), auditReport
    );
  }

  private static BenchmarkRunState executeBenchmark(
    ApplicationContext sourceContext,
    BenchmarkOptions options,
    BenchmarkMetrics metrics
  ) throws IOException, InterruptedException {
    BenchmarkRunState state = new BenchmarkRunState();
    try (BenchmarkWorkspace workspace = BenchmarkWorkspace.create(sourceContext, options);
         ApplicationContext context = ApplicationFactory.create(workspace.config(), metrics, metrics, metrics)) {
      BenchmarkExecution execution = new BenchmarkExecution(sourceContext, context, workspace, options, metrics, state);
      List<BenchmarkScenario.Turn> turns = BenchmarkScenario.create(options.turns());
      for (int turnIndex = 0; turnIndex < turns.size(); turnIndex++) {
        executeTurn(execution, turns.get(turnIndex), turnIndex + 1);
      }
    }
    return state;
  }

  private static void executeTurn(
    BenchmarkExecution execution,
    BenchmarkScenario.Turn turn,
    int turnNumber
  ) throws IOException, InterruptedException {
    execution.handleUserTurn(turn.prompt());
    execution.awaitKnowledgeGraph();
    execution.observeMemory();
    BenchmarkMetrics.TurnResponses responses = execution.lastTurn();
    execution.recordReplacement(responses);
    if (turn.isProbe()) {
      execution.recordProbe(turn, responses, turnNumber);
    }
  }

  private static void awaitKnowledgeGraph(
    ApplicationContext context,
    BenchmarkWorkspace workspace,
    BenchmarkOptions options
  ) throws IOException, InterruptedException {
    if (options.knowledgeGraph()
      && !context.derivedMemoryTaskQueue().awaitIdle(workspace.config().summaryRequestTimeoutSeconds(), TimeUnit.SECONDS)) {
      throw new IOException("Timed out while waiting for benchmark knowledge-graph work.");
    }
  }

  private static void recordProbe(
    ApplicationContext context,
    BenchmarkRunState state,
    BenchmarkScenario.Turn turn,
    BenchmarkMetrics.TurnResponses responses,
    int turnNumber
  ) {
    var graph = context.knowledgeGraphService().current();
    BenchmarkProbeScorer.Score score = score(turn, responses);
    state.recordProbe(turn, score, new BenchmarkProbeResult(
      turnNumber, turn.prompt(), responses.draftResponse(), responses.finalResponse(), turn.expectedTerms(),
      turn.forbiddenTerms(), turn.probeKind(), score.draftPassed(), score.finalPassed(), graph.revision(),
      graph.entities().size(), graph.entities().keySet().stream().mapToInt(entity -> graph.factsBySubject(entity).size()).sum()
    ));
  }

  private static BenchmarkProbeScorer.Score score(
    BenchmarkScenario.Turn turn,
    BenchmarkMetrics.TurnResponses responses
  ) {
    return turn.probeKind() == BenchmarkScenario.ProbeKind.VALIDATION
      ? BenchmarkProbeScorer.scoreForbidden(
        responses.draftResponse(), responses.finalResponse(), turn.expectedTerms(), turn.forbiddenTerms()
      )
      : BenchmarkProbeScorer.score(responses.draftResponse(), responses.finalResponse(), turn.expectedTerms());
  }

  private static Path writeAuditReport(
    ApplicationContext context,
    BenchmarkOptions options,
    List<BenchmarkProbeResult> probes,
    List<String> graphFailures
  ) throws IOException {
    Path outputDirectory = Files.createDirectories(context.config().baseDir().resolve("benchmark-results"));
    Path report = outputDirectory.resolve("benchmark-" + Instant.now().toString().replace(':', '-') + ".md");
    StringBuilder content = new StringBuilder("# Assistant Benchmark Audit\n\n")
      .append("- Model: `").append(displayModel(options)).append("`\n")
      .append("- Validation: ").append(options.validation()).append("\n")
      .append("- Cache-buster: ").append(options.cacheBuster()).append("\n")
      .append("- Knowledge graph: ").append(options.knowledgeGraph()).append("\n")
      .append("- Graph updates rejected: ").append(graphFailures.size()).append("\n")
      .append("- Rejection semantics: the complete candidate update was discarded; the previously stored graph remained unchanged.\n");
    for (String failure : graphFailures) {
      content.append("  - ").append(failure).append("\n");
    }
    content.append('\n');
    for (BenchmarkProbeResult probe : probes) {
      content.append("## Turn ").append(probe.turn()).append(probe.finalPassed() ? " — PASS\n\n" : " — FAIL\n\n")
        .append("Probe type: ").append(probe.probeKind()).append("\n\n")
        .append("Prompt: ").append(probe.prompt()).append("\n\n")
        .append(probe.probeKind() == BenchmarkScenario.ProbeKind.VALIDATION
          ? "Required terms after correction: `" + String.join("`, `", probe.expectedTerms())
            + "`\n\nForbidden assertion: `" + String.join("`, `", probe.forbiddenTerms()) + "`\n\n"
          : "Expected terms: `" + String.join("`, `", probe.expectedTerms()) + "`\n\n")
        .append("Draft passed: ").append(probe.draftPassed()).append("\n\n")
        .append("Draft response:\n\n```text\n").append(probe.draftResponse()).append("\n```\n\n")
        .append("Validator replaced draft: ").append(probe.replaced()).append("\n\n")
        .append("Final passed: ").append(probe.finalPassed()).append("\n\n")
        .append("Final response:\n\n```text\n").append(probe.finalResponse()).append("\n```\n\n")
        .append("Graph: revision ").append(probe.graphRevision())
        .append(", ").append(probe.graphEntities()).append(" entities, ")
        .append(probe.graphFacts()).append(" facts.\n\n");
    }
    Files.writeString(report, content, StandardCharsets.UTF_8);
    return report;
  }

  private static void verifyManagedModel(ApplicationContext context, BenchmarkOptions options) {
    if (!context.config().usesManagedMlxServer()) {
      return;
    }
    String configuredModel = context.config().mlxServerConfig().modelPath().getFileName().toString();
    if (!options.model().isBlank() && !normalize(configuredModel).contains(normalize(options.model()))) {
      throw new IllegalArgumentException(
        "The managed MLX backend currently runs '" + configuredModel + "'. Configure and start '"
          + options.model() + "' before running this benchmark; a second model will not be loaded alongside it."
      );
    }
    if (!BENCHMARK_KV_LIMIT.matcher(context.config().mlxServerConfig().arguments()).find()) {
      throw new IllegalArgumentException(
        "The fixed benchmark requires '--max-kv-size 4096' in backend.mlx.arguments. "
          + "Restart the managed MLX server with that setting before benchmarking."
      );
    }
    var maxTokens = MAX_OUTPUT_TOKENS.matcher(context.config().mlxServerConfig().arguments());
    if (!maxTokens.find() || Integer.parseInt(maxTokens.group(1)) < 2048) {
      throw new IllegalArgumentException(
        "The fixed benchmark requires at least '--max-tokens 2048' in backend.mlx.arguments so graph JSON is not truncated. "
          + "Story responses remain limited to 128 tokens by their request."
      );
    }
  }

  private static String normalize(String value) {
    return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }

  static String displayModel(BenchmarkOptions options) {
    return options.model().isBlank() ? "loaded backend model" : options.model();
  }

  private record BenchmarkExecution(
    ApplicationContext sourceContext,
    ApplicationContext context,
    BenchmarkWorkspace workspace,
    BenchmarkOptions options,
    BenchmarkMetrics metrics,
    BenchmarkRunState state
  ) {
    private void handleUserTurn(String prompt) throws IOException, InterruptedException {
      context.storySessionService().handleUserTurn(prompt);
    }

    private void awaitKnowledgeGraph() throws IOException, InterruptedException {
      BenchmarkRunner.awaitKnowledgeGraph(context, workspace, options);
    }

    private void observeMemory() {
      state.observeMemory(readServerMemoryBytes());
    }

    private Long readServerMemoryBytes() {
      if (sourceContext.managedMlxServer() == null) {
        return null;
      }
      try {
        Process process = new ProcessBuilder(
          "ps", "-o", "rss=", "-p", Long.toString(sourceContext.managedMlxServer().processId())
        ).start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        return process.waitFor() == 0 && !output.isBlank() ? Long.parseLong(output) * 1024 : null;
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
        return null;
      } catch (IOException | NumberFormatException _) {
        return null;
      }
    }

    private BenchmarkMetrics.TurnResponses lastTurn() {
      return metrics.lastTurn();
    }

    private void recordReplacement(BenchmarkMetrics.TurnResponses responses) {
      state.recordReplacement(options, responses);
    }

    private void recordProbe(
      BenchmarkScenario.Turn turn,
      BenchmarkMetrics.TurnResponses responses,
      int turnNumber
    ) {
      BenchmarkRunner.recordProbe(context, state, turn, responses, turnNumber);
    }
  }

  private static final class BenchmarkRunState {
    private final List<BenchmarkProbeResult> probeResults = new ArrayList<>();
    private int probes;
    private int passed;
    private int entityErrors;
    private int stateErrors;
    private int validationReplacements;
    private int validationImprovements;
    private int validationRegressions;
    private int validationProbes;
    private int passedValidationProbes;
    private Long peakServerMemoryBytes;

    private void observeMemory(Long memoryBytes) {
      if (memoryBytes != null) {
        peakServerMemoryBytes = peakServerMemoryBytes == null
          ? memoryBytes
          : Math.max(peakServerMemoryBytes, memoryBytes);
      }
    }

    private void recordReplacement(BenchmarkOptions options, BenchmarkMetrics.TurnResponses responses) {
      if (options.validation() && !responses.draftResponse().equals(responses.finalResponse())) {
        validationReplacements++;
      }
    }

    private void recordProbe(
      BenchmarkScenario.Turn turn,
      BenchmarkProbeScorer.Score score,
      BenchmarkProbeResult result
    ) {
      probeResults.add(result);
      recordValidationEffect(score);
      if (turn.probeKind() == BenchmarkScenario.ProbeKind.VALIDATION) {
        validationProbes++;
        passedValidationProbes += score.finalPassed() ? 1 : 0;
        return;
      }
      probes++;
      if (score.finalPassed()) {
        passed++;
      } else if (turn.errorType() == BenchmarkScenario.ErrorType.ENTITY) {
        entityErrors++;
      } else {
        stateErrors++;
      }
    }

    private void recordValidationEffect(BenchmarkProbeScorer.Score score) {
      if (score.improved()) {
        validationImprovements++;
      } else if (score.regressed()) {
        validationRegressions++;
      }
    }
  }
}
