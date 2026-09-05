package nl.llm.storyteller.cli.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BenchmarkScenarioTest {
  @ParameterizedTest
  @ValueSource(ints = {10, 50, 100})
  @DisplayName("""
    Given a supported benchmark length,
    When the fixed scenario is created,
    Then it should contain exactly that many turns and end with a fact probe
    """)
  void createsAReproducibleScenarioOfTheRequestedLength(int turns) {
    var scenario = BenchmarkScenario.create(turns);

    assertEquals(turns, scenario.size());
    assertTrue(scenario.getLast().isProbe());
    assertTrue(scenario.getFirst().prompt().contains("Alice lives in Paris"));
    String completeScenario = scenario.toString().toLowerCase();
    assertTrue(completeScenario.contains("trusts"));
    assertTrue(completeScenario.contains("wears"));
    assertFalse(completeScenario.contains("brother"));
    assertFalse(completeScenario.contains("baker"));
  }
}
