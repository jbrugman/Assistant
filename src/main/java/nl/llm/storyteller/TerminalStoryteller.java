package nl.llm.storyteller;

import nl.llm.storyteller.service.StoryExportService;
import nl.llm.storyteller.service.StorySessionService;
import org.jline.reader.Binding;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Terminal;

import java.io.IOException;

final class TerminalStoryteller {
    private static final String APP_NAME = "storyteller";
    private static final String EXIT_COMMAND = "/exit";
    private static final String QUIT_COMMAND = "/quit";
    private static final String EXPORT_COMMAND = "/export";
    private static final String EXPORT_ALL_OPTION = "-all";
    private static final String EXPORT_INTRO_OPTION = "-intro";
    private static final String EXPORT_CLEAN_OPTION = "-clean";
    private static final String CONTINUE_STORY_WIDGET = "continue-story";
    private static final String RESET_WIDGET = "reset-behavior";
    private static final String UNDO_WIDGET = "undo-last-turn";
    private static final String LAST_TURN_WIDGET = "show-last-turn";

    private final ApplicationContext context;
    private final TerminalRenderer renderer;
    private final LineReader reader;

    TerminalStoryteller(Terminal terminal, ApplicationContext context) {
        this.context = context;
        this.renderer = new TerminalRenderer(terminal);
        this.reader = LineReaderBuilder.builder().terminal(terminal).appName(APP_NAME).build();
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
        if (!userInput.startsWith(EXPORT_COMMAND)) {
            return false;
        }
        StoryExportService.ExportMode exportMode = parseExportMode(userInput);
        if (exportMode == null) {
            renderer.printError("Export command error", "Use /export, /export -intro, /export -clean, or /export -all.");
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
            renderer.printError(context.config().lmStudioRequestErrorText(), ex.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException ex) {
            renderer.printError(context.config().lmStudioRequestErrorText(), ex.getMessage());
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
            renderer.printError(context.config().lmStudioRequestErrorText(), ex.getMessage());
            Thread.currentThread().interrupt();
        } catch (IOException ex) {
            renderer.printError(context.config().lmStudioRequestErrorText(), ex.getMessage());
        } catch (RuntimeException ex) {
            renderer.printError(context.config().processHistoryErrorText(), ex.getMessage());
        }
    }

    @FunctionalInterface
    private interface CheckedUiAction {
        void run() throws IOException, InterruptedException;
    }
}
