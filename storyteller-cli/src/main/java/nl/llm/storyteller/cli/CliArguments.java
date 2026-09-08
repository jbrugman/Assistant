package nl.llm.storyteller.cli;

import java.util.Arrays;
import java.util.UUID;

record CliArguments(String sessionId) {
  static CliArguments parse(String... arguments) {
    String sessionId = null;
    var iterator = Arrays.asList(arguments).iterator();
    while (iterator.hasNext()) {
      String argument = iterator.next();
      if ("--session".equals(argument)) {
        if (!iterator.hasNext()) {
          throw new IllegalArgumentException("Missing session ID after --session.");
        }
        sessionId = iterator.next();
      } else if (argument.startsWith("--session=")) {
        sessionId = argument.substring("--session=".length());
      } else {
        throw new IllegalArgumentException("Unknown CLI argument: " + argument);
      }
    }
    return new CliArguments(sessionId == null ? null : UUID.fromString(sessionId.trim()).toString());
  }
}

