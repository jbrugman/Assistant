package nl.llm.storyteller.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiConfigTest {
  @ParameterizedTest
  @ValueSource(ints = {-1, 65_536})
  @DisplayName("""
    Given an API port outside the supported range,
    When the API configuration is created,
    Then it should reject the invalid port
    """)
  void shouldRejectInvalidPort(int port) {
    assertThrows(IllegalArgumentException.class, () -> new ApiConfig(
      "127.0.0.1",
      port,
      Path.of("memory/test"),
      "sa",
      "",
      Duration.ofMinutes(60)
    ));
  }
}
