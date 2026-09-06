package nl.llm.storyteller.cli;

import nl.llm.storyteller.core.ApplicationContext;
import nl.llm.storyteller.core.service.StoryExportService;
import nl.llm.storyteller.core.service.StorySessionService;
import nl.llm.storyteller.cli.benchmark.BenchmarkExecutor;
import nl.llm.storyteller.cli.benchmark.BenchmarkOptions;
import nl.llm.storyteller.cli.benchmark.BenchmarkRunner;
import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;

import java.io.IOException;

final class TerminalStoryteller {
  private static final String APP_NAME = "storyteller";
  private static final String EXIT_COMMAND = "/exit";
  private static final String QUIT_COMMAND = "/quit";
  private static final String EXPORT_COMMAND = "/export";
  private static final String IMAGE_COMMAND = "/image";
  private static final String GRAPH_COMMAND = "/graph";
  private static final String BENCHMARK_COMMAND = "/benchmark";
  private static final String GRAPH_GENERATE_COMMAND = "/graph -generate";
  private static final String GRAPH_FILL_COMMAND = "/graph -fill";
  private static final String GRAPH_RESET_COMMAND = "/graph -reset";
  private static final String EXPORT_ALL_OPTION = "-all";
  private static final String EXPORT_INTRO_OPTION = "-intro";
  private static final String EXPORT_CLEAN_OPTION = "-clean";
  private static final String EXPORT_ZIP_OPTION = "-zip";
  private static final String CONTINUE_STORY_WIDGET = "continue-story";
  private static final String RESET_WIDGET = "reset-behavior";
  private static final String UNDO_WIDGET = "undo-last-turn";
  private static final String LAST_TURN_WIDGET = "show-last-turn";

  private final ApplicationContext context;
  private final TerminalRenderer renderer;
  private final LineReader reader;
  private final ClipboardImageReader clipboardImageReader;
  private final BenchmarkExecutor benchmarkExecutor;

  TerminalStoryteller(Terminal terminal, ApplicationContext context) {
    this(terminal, context, new ClipboardImageReader(), new BenchmarkRunner());
  }

  TerminalStoryteller(
    Terminal terminal,
    ApplicationContext context,
    ClipboardImageReader clipboardImageReader,
    BenchmarkExecutor benchmarkExecutor
  ) {
    this.context = context;
    this.renderer = new TerminalRenderer(terminal);
    this.reader = LineReaderBuilder.builder().terminal(terminal).appName(APP_NAME).build();
    this.clipboardImageReader = clipboardImageReader;
    this.benchmarkExecutor = benchmarkExecutor;
    registerShortcuts();
  }

  void run() {
    renderer.printBanner(context.config());
    String userInput;
    while ((userInput = readUserInput()) != null) {
      if (shouldExit(userInput)) {
        return;
      }
      if (handleCommand(userInput)) {
        continue;
      }
      if (!userInput.isEmpty() && !handleUserTurn(userInput)) {
        return;
      }
    }
  }

  private void registerShortcuts() {
    registerContinueStoryShortcut();
    registerResetShortcut();
    registerUndoShortcut();
    registerLastTurnShortcut();
  }

  private void registerContinueStoryShortcut() {
    reader.getWidgets().put(CONTINUE_STORY_WIDGET, () -> {
      reader.getBuffer().clear();
      reader.getBuffer().write(context.config().continueStoryCommand());
      reader.callWidget(LineReader.ACCEPT_LINE);
      return true;
    });
    bindShortcut(CONTINUE_STORY_WIDGET, 'G', 'g');
  }

  private void registerResetShortcut() {
    reader.getWidgets().put(RESET_WIDGET, () -> {
      reader.getBuffer().clear();
      renderer.printMessage(context.config().resetSentText());
      executeUiAction(() -> context.storySessionService().handleUserTurn(context.config().resetStoryCommand()));
      return true;
    });
    bindShortcut(RESET_WIDGET, 'W', 'w');
  }

  private void registerUndoShortcut() {
    reader.getWidgets().put(UNDO_WIDGET, () -> {
      renderer.printMessage(context.config().undoSentText());
      executeUiAction(() -> {
        StorySessionService.UndoResult result = context.storySessionService().undoLastTurnAndReset();
        reader.getBuffer().clear();
        if (!result.hasRestoredUserInput()) {
          renderer.printMessage(context.config().noStoryTurnToUndoText());
          return;
        }
        renderer.printLastPersistedTurn(context.config(), context.storySessionService().loadLastTurn());
        renderer.printMessage(context.config().undoRestoredText());
        reader.getBuffer().write(result.restoredUserInput());
      });
      return true;
    });
    bindShortcut(UNDO_WIDGET, 'U', 'u');
  }

  private void registerLastTurnShortcut() {
    reader.getWidgets().put(LAST_TURN_WIDGET, () -> {
      renderer.printLastPersistedTurn(context.config(), context.storySessionService().loadLastTurn());
      return true;
    });
    bindShortcut(LAST_TURN_WIDGET, 'L', 'l');
  }

  private void bindShortcut(String widgetName, char ctrlKey, char altKey) {
    Reference binding = new Reference(widgetName);
    bindShortcut(LineReader.MAIN, binding, ctrlKey, altKey);
    bindShortcut(LineReader.EMACS, binding, ctrlKey, altKey);
    bindShortcut(LineReader.VIINS, binding, ctrlKey, altKey);
  }

  private void bindShortcut(String keyMapName, Reference binding, char ctrlKey, char altKey) {
    KeyMap<Binding> keyMap = reader.getKeyMaps().get(keyMapName);
    if (keyMap != null) {
      keyMap.bind(binding, KeyMap.ctrl(ctrlKey));
      keyMap.bind(binding, KeyMap.alt(altKey));
    }
  }

  private String readUserInput() {
    while (true) {
      try {
        return reader.readLine("> ").trim();
      } catch (UserInterruptException _) {
        // Let the user cancel the current line without exiting the app.
      } catch (EndOfFileException _) {
        return null;
      }
    }
  }

  private boolean shouldExit(String userInput) {
    return EXIT_COMMAND.equalsIgnoreCase(userInput) || QUIT_COMMAND.equalsIgnoreCase(userInput);
  }

  private boolean handleCommand(String userInput) {
    if (BENCHMARK_COMMAND.equalsIgnoreCase(userInput)
      || userInput.regionMatches(true, 0, BENCHMARK_COMMAND + " ", 0, BENCHMARK_COMMAND.length() + 1)) {
      handleBenchmarkCommand(userInput);
      return true;
    }
    if (GRAPH_FILL_COMMAND.equalsIgnoreCase(userInput)) {
      handleGraphFillCommand();
      return true;
    }
    if (GRAPH_GENERATE_COMMAND.equalsIgnoreCase(userInput)) {
      var graph = context.knowledgeGraphInitializer().generateEmpty();
      renderer.printMessage("Empty knowledge graph generated locally (revision %d)."
        .formatted(graph.revision()));
      return true;
    }
    if (GRAPH_RESET_COMMAND.equalsIgnoreCase(userInput)) {
      var result = context.knowledgeGraphManagementService().resetTurnBasedItems();
      renderer.printMessage(
        "Turn-based graph data reset: %d entities and %d facts removed (revision %d)."
          .formatted(result.entitiesRemoved(), result.factsRemoved(), result.revision())
      );
      return true;
    }
    if (GRAPH_COMMAND.equalsIgnoreCase(userInput)) {
      renderer.printMessage(KnowledgeGraphFormatter.format(
        context.knowledgeGraphService().current(),
        context.config().knowledgeGraphFile()
      ));
      return true;
    }
    if (userInput.regionMatches(true, 0, GRAPH_COMMAND + " ", 0, GRAPH_COMMAND.length() + 1)) {
      renderer.printError(
        "Graph command error",
        "Use /graph, /graph -generate, /graph -fill, or /graph -reset."
      );
      return true;
    }
    if (IMAGE_COMMAND.equalsIgnoreCase(userInput) || userInput.regionMatches(true, 0, IMAGE_COMMAND + " ", 0, IMAGE_COMMAND.length() + 1)) {
      handleImageCommand(userInput);
      return true;
    }
    if (!userInput.startsWith(EXPORT_COMMAND)) {
      return false;
    }
    if ((EXPORT_COMMAND + " " + EXPORT_ZIP_OPTION).equalsIgnoreCase(userInput.trim())) {
      exportSessionBundle();
      return true;
    }
    StoryExportService.ExportMode exportMode = parseExportMode(userInput);
    if (exportMode == null) {
      renderer.printError(
        "Export command error",
        "Use /export, /export -intro, /export -clean, /export -all, or /export -zip."
      );
      return true;
    }
    try {
      var path = context.storyExportService().export(exportMode);
      renderer.printMessage("Story exported to " + path.getFileName());
    } catch (RuntimeException ex) {
      renderer.printError(context.config().processHistoryErrorText(), ex.getMessage());
    }
    return true;
  }

  private void exportSessionBundle() {
    try {
      var path = context.storyExportService().exportSessionBundle(context.config());
      renderer.printMessage("Session ZIP exported to " + path.getFileName());
    } catch (RuntimeException ex) {
      renderer.printError(context.config().processHistoryErrorText(), ex.getMessage());
    }
  }

  private void handleBenchmarkCommand(String userInput) {
    try {
      BenchmarkOptions options = BenchmarkOptions.parse(userInput);
      renderer.printMessage("Benchmark started; the normal story history is not used or changed.");
      renderer.printMessage(benchmarkExecutor.run(context, options).format());
    } catch (IllegalArgumentException ex) {
      renderer.printError("Benchmark command error", ex.getMessage());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      renderer.printError(context.config().backendRequestErrorText(), ex.getMessage());
    } catch (IOException | RuntimeException ex) {
      renderer.printError("Benchmark error", ex.getMessage());
    }
  }

  private void handleGraphFillCommand() {
    try {
      var result = context.knowledgeGraphFillService().fill();
      renderer.printMessage("Knowledge graph filled from fixed protagonists: %d entities, %d facts."
        .formatted(result.entities(), result.facts()));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      renderer.printError(context.config().backendRequestErrorText(), ex.getMessage());
    } catch (IOException | RuntimeException ex) {
      renderer.printError("Knowledge graph fill error", ex.getMessage());
    }
  }

  private void handleImageCommand(String userInput) {
    String instruction = userInput.substring(IMAGE_COMMAND.length()).trim();
    if (instruction.isBlank()) {
      renderer.printError("Image command error", "Use /image <instruction> after copying an image to the clipboard.");
      return;
    }

    try {
      String imageDataUrl = clipboardImageReader.readPngDataUrl();
      renderer.printMessage(context.storySessionService().handleImageTurn(instruction, imageDataUrl));
    } catch (InterruptedException ex) {
      renderer.printError(context.config().backendRequestErrorText(), ex.getMessage());
      Thread.currentThread().interrupt();
    } catch (IOException ex) {
      renderer.printError(context.config().backendRequestErrorText(), ex.getMessage());
    } catch (RuntimeException ex) {
      renderer.printError("Image command error", ex.getMessage());
    }
  }

  private StoryExportService.ExportMode parseExportMode(String userInput) {
    String trimmed = userInput.trim();
    if (EXPORT_COMMAND.equalsIgnoreCase(trimmed) || (EXPORT_COMMAND + " " + EXPORT_INTRO_OPTION).equalsIgnoreCase(trimmed)) {
      return StoryExportService.ExportMode.INTRO;
    }
    if ((EXPORT_COMMAND + " " + EXPORT_ALL_OPTION).equalsIgnoreCase(trimmed)) {
      return StoryExportService.ExportMode.ALL;
    }
    if ((EXPORT_COMMAND + " " + EXPORT_CLEAN_OPTION).equalsIgnoreCase(trimmed)) {
      return StoryExportService.ExportMode.CLEAN;
    }
    return null;
  }

  private boolean handleUserTurn(String userInput) {
    try {
      renderer.printMessage(context.storySessionService().handleUserTurn(userInput));
      return true;
    } catch (InterruptedException ex) {
            renderer.printError(context.config().backendRequestErrorText(), ex.getMessage());
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException ex) {
            renderer.printError(context.config().backendRequestErrorText(), ex.getMessage());
      return true;
    } catch (RuntimeException ex) {
      renderer.printError(context.config().processHistoryErrorText(), ex.getMessage());
      return true;
    }
  }

  private void executeUiAction(CheckedUiAction action) {
    try {
      action.run();
    } catch (InterruptedException ex) {
            renderer.printError(context.config().backendRequestErrorText(), ex.getMessage());
      Thread.currentThread().interrupt();
    } catch (IOException ex) {
            renderer.printError(context.config().backendRequestErrorText(), ex.getMessage());
    } catch (RuntimeException ex) {
      renderer.printError(context.config().processHistoryErrorText(), ex.getMessage());
    }
  }

  @FunctionalInterface
  private interface CheckedUiAction {
    void run() throws IOException, InterruptedException;
  }
}
