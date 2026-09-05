package nl.llm.storyteller.cli.benchmark;

import java.util.List;

record BenchmarkProbeResult(
  int turn,
  String prompt,
  String draftResponse,
  String finalResponse,
  List<String> expectedTerms,
  List<String> forbiddenTerms,
  BenchmarkScenario.ProbeKind probeKind,
  boolean draftPassed,
  boolean finalPassed,
  long graphRevision,
  int graphEntities,
  int graphFacts
) {
  boolean replaced() {
    return !draftResponse.equals(finalResponse);
  }
}
