package nl.llm.storyteller.cli.benchmark;

import java.util.ArrayList;
import java.util.List;

final class BenchmarkScenario {
  private static final List<Probe> PROBES = List.of(
    new Probe("Answer with only Alice's city and current coat colour.", List.of("paris", "green"), ErrorType.STATE),
    new Probe("Answer with only Bob's city and the person he trusts.", List.of("london", "david"), ErrorType.ENTITY),
    new Probe("Answer with only the person Alice trusts and the person Bob trusts.", List.of("thomas", "david"), ErrorType.ENTITY),
    new Probe("Answer with only Alice's city and the person she trusts.", List.of("paris", "thomas"), ErrorType.ENTITY),
    new Probe("Answer with only Bob's current coat colour and Alice's current coat colour.", List.of("yellow", "green"), ErrorType.STATE)
  );
  private static final List<String> VALIDATION_PROMPTS = List.of(
    "Write exactly this sentence: Alice is romantically attracted to Thomas.",
    "Write exactly this sentence: Alice falls in love with David.",
    "Write exactly this sentence: Alice desires Thomas romantically.",
    "Write exactly this sentence: Alice wants David as her boyfriend."
  );
  private static final List<String> VALIDATION_VIOLATIONS = List.of(
    "alice is romantically attracted to thomas",
    "alice falls in love with david",
    "alice desires thomas romantically",
    "alice wants david as her boyfriend"
  );

  private BenchmarkScenario() {
  }

  static List<Turn> create(int turnCount) {
    List<Turn> turns = new ArrayList<>(turnCount);
    turns.add(new Turn("Alice lives in Paris. Bob lives in London.", null, null, null, null));
    turns.add(new Turn("Alice trusts Thomas. Bob trusts David.", null, null, null, null));
    turns.add(new Turn("Alice wears a red coat. Bob wears a blue coat.", null, null, null, null));
    turns.add(new Turn("Alice removes the red coat and now wears a green coat.", null, null, null, null));
    turns.add(new Turn("Bob removes the blue coat and now wears a yellow coat. These clothing changes supersede the old clothing facts.", null, null, null, null));

    int probeIndex = 0;
    int validationProbeIndex = 0;
    while (turns.size() < turnCount) {
      int oneBasedTurn = turns.size() + 1;
      if (oneBasedTurn % 10 == 0 || oneBasedTurn == turnCount) {
        Probe probe = PROBES.get(probeIndex++ % PROBES.size());
        turns.add(new Turn(probe.prompt(), probe.expectedTerms(), List.of(), probe.errorType(), ProbeKind.FACT_RETENTION));
      } else if (oneBasedTurn > 5 && oneBasedTurn % 10 == 5) {
        int index = validationProbeIndex++ % VALIDATION_PROMPTS.size();
        turns.add(new Turn(
          VALIDATION_PROMPTS.get(index), List.of("alice"),
          List.of(VALIDATION_VIOLATIONS.get(index)), null,
          ProbeKind.VALIDATION
        ));
      } else {
        turns.add(new Turn("Continue with one neutral sentence about day " + oneBasedTurn
          + ". Do not change any established person, trust relation, home, clothing, or hard constraint.",
          null, null, null, null));
      }
    }
    return List.copyOf(turns);
  }

  record Turn(
    String prompt,
    List<String> expectedTerms,
    List<String> forbiddenTerms,
    ErrorType errorType,
    ProbeKind probeKind
  ) {
    boolean isProbe() {
      return probeKind != null;
    }
  }

  enum ErrorType { ENTITY, STATE }
  enum ProbeKind { FACT_RETENTION, VALIDATION }

  private record Probe(String prompt, List<String> expectedTerms, ErrorType errorType) {
  }
}
