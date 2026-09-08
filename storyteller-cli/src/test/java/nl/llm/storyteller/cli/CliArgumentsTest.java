package nl.llm.storyteller.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliArgumentsTest {
  private static final String SESSION_ID = "05dbf813-8f2b-45f8-abad-93efa01f1199";

  @ParameterizedTest
  @MethodSource("sessionArguments")
  @DisplayName("Given a session argument, when CLI arguments are parsed, then its session ID should be selected")
  void shouldParseSessionId(String[] arguments) {
    assertEquals(SESSION_ID, CliArguments.parse(arguments).sessionId());
  }

  private static Stream<Arguments> sessionArguments() {
    return Stream.of(
      Arguments.of((Object) new String[]{"--session", SESSION_ID}),
      Arguments.of((Object) new String[]{"--session=" + SESSION_ID})
    );
  }
}
