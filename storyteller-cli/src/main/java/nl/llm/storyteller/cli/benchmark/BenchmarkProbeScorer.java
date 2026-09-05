package nl.llm.storyteller.cli.benchmark;

import java.util.List;
import java.util.Locale;

final class BenchmarkProbeScorer {
  private BenchmarkProbeScorer() {
  }

  static Score score(String draft, String result, List<String> expectedTerms) {
    boolean draftPassed = containsExpectedTerms(draft, expectedTerms);
    boolean finalPassed = containsExpectedTerms(result, expectedTerms);
    boolean replaced = !draft.equals(result);
    return new Score(
      draftPassed,
      finalPassed,
      replaced,
      replaced && !draftPassed && finalPassed,
      replaced && draftPassed && !finalPassed
    );
  }

  static Score scoreForbidden(
    String draft,
    String result,
    List<String> requiredTerms,
    List<String> forbiddenTerms
  ) {
    boolean draftPassed = containsExpectedTerms(draft, requiredTerms) && excludesForbiddenTerms(draft, forbiddenTerms);
    boolean finalPassed = containsExpectedTerms(result, requiredTerms) && excludesForbiddenTerms(result, forbiddenTerms);
    boolean replaced = !draft.equals(result);
    return new Score(
      draftPassed,
      finalPassed,
      replaced,
      replaced && !draftPassed && finalPassed,
      replaced && draftPassed && !finalPassed
    );
  }

  private static boolean containsExpectedTerms(String response, List<String> expectedTerms) {
    String normalized = response.toLowerCase(Locale.ROOT);
    return expectedTerms.stream().allMatch(normalized::contains);
  }

  private static boolean excludesForbiddenTerms(String response, List<String> forbiddenTerms) {
    String normalized = response.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    return forbiddenTerms.stream().noneMatch(normalized::contains);
  }

  record Score(boolean draftPassed, boolean finalPassed, boolean replaced, boolean improved, boolean regressed) {
  }
}
