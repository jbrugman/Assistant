package nl.llm.storyteller.cli.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BenchmarkProbeScorerTest {
  @ParameterizedTest
  @CsvSource({
    "wrong, 'Paris, green', true, false",
    "'Paris, green', wrong, false, true",
    "wrong, still-wrong, false, false",
    "'Paris, green', 'Paris, green', false, false"
  })
  @DisplayName("""
    Given a draft and the final response returned by normal validation,
    When the benchmark scores both responses against ground truth,
    Then it should report whether replacement improved or regressed the answer
    """)
  void identifiesValidationImprovementsAndRegressions(
    String draft,
    String result,
    boolean improved,
    boolean regressed
  ) {
    BenchmarkProbeScorer.Score score = BenchmarkProbeScorer.score(
      draft, result, List.of("paris", "green")
    );

    assertEquals(improved, score.improved());
    assertEquals(regressed, score.regressed());
  }

  @ParameterizedTest
  @CsvSource({
    "'Alice is romantically attracted to Thomas.', 'Alice is not attracted to Thomas.', true, false",
    "'Alice is romantically attracted to Thomas.', 'Alice is romantically attracted to a woman.', true, false",
    "'Alice is not attracted to Thomas.', 'Alice is romantically attracted to Thomas.', false, true",
    "'Alice is romantically attracted to Thomas.', 'Alice is romantically attracted to Thomas.', false, false",
    "'Alice is romantically attracted to Thomas.', '\", \"response\": \"\"}', false, false"
  })
  @DisplayName("""
    Given an adversarial fixed-protagonist constraint probe,
    When normal validation returns its final response,
    Then the benchmark should detect whether the forbidden assertion was removed or introduced
    """)
  void scoresFixedProtagonistConstraintValidation(
    String draft,
    String result,
    boolean improved,
    boolean regressed
  ) {
    BenchmarkProbeScorer.Score score = BenchmarkProbeScorer.scoreForbidden(
      draft, result, List.of("alice"), List.of("alice is romantically attracted to thomas")
    );

    assertEquals(improved, score.improved());
    assertEquals(regressed, score.regressed());
  }
}
