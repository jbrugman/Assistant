package nl.llm.storyteller.cli.benchmark;

import java.util.Locale;

public record BenchmarkOptions(
  String model,
  int turns,
  boolean validation,
  boolean cacheBuster,
  boolean knowledgeGraph
) {
  private static final int DEFAULT_TURNS = 50;

  public static BenchmarkOptions parse(String command) {
    String[] arguments = command.trim().split("\\s+");
    String model = "";
    int optionStart = 1;
    if (arguments.length > 1 && arguments[1].startsWith("-") && !arguments[1].startsWith("--")) {
      if (arguments[1].length() == 1) {
        throw new IllegalArgumentException(usage());
      }
      model = arguments[1].substring(1);
      optionStart = 2;
    }

    int turns = DEFAULT_TURNS;
    boolean validation = true;
    boolean cacheBuster = true;
    boolean knowledgeGraph = true;
    for (int index = optionStart; index < arguments.length; index++) {
      String argument = arguments[index];
      if (argument.startsWith("--turns=")) {
        turns = parseTurns(argument.substring("--turns=".length()));
      } else if (argument.startsWith("--validation=")) {
        validation = parseSwitch("validation", argument.substring("--validation=".length()));
      } else if (argument.startsWith("--cache-buster=")) {
        cacheBuster = parseSwitch("cache-buster", argument.substring("--cache-buster=".length()));
      } else if (argument.startsWith("--knowledge-graph=")) {
        knowledgeGraph = parseSwitch("knowledge-graph", argument.substring("--knowledge-graph=".length()));
      } else {
        throw new IllegalArgumentException("Unknown benchmark option: " + argument + "\n" + usage());
      }
    }
    return new BenchmarkOptions(model, turns, validation, cacheBuster, knowledgeGraph);
  }

  public static String usage() {
    return "Use /benchmark [-<model>] "
      + "[--turns=50] [--validation=on|off] [--cache-buster=on|off] [--knowledge-graph=on|off].";
  }

  private static int parseTurns(String value) {
    try {
      int turns = Integer.parseInt(value);
      if (turns < 10 || turns > 100) {
        throw new IllegalArgumentException("Benchmark turns must be between 10 and 100.");
      }
      return turns;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Benchmark turns must be a number between 10 and 100.", ex);
    }
  }

  private static boolean parseSwitch(String name, String value) {
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "on" -> true;
      case "off" -> false;
      default -> throw new IllegalArgumentException("Benchmark option " + name + " must be on or off.");
    };
  }
}
