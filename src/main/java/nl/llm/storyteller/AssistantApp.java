package nl.llm.storyteller;

import nl.llm.storyteller.service.CanonicalStateManager;
import nl.llm.storyteller.service.HistoryStore;
import nl.llm.storyteller.service.LMStudioClient;
import nl.llm.storyteller.service.CanonicalStatePromptBuilder;
import nl.llm.storyteller.service.GameModeDefinitionParser;
import nl.llm.storyteller.service.PromptAssemblyService;
import nl.llm.storyteller.service.PromptResourceLoader;
import nl.llm.storyteller.service.PromptTemplateService;
import nl.llm.storyteller.service.RecentSummaryManager;
import nl.llm.storyteller.service.RecentSummaryPromptBuilder;
import nl.llm.storyteller.service.ResilientChatClient;
import nl.llm.storyteller.service.ResponseGuard;
import nl.llm.storyteller.service.StoryChatPromptBuilder;
import nl.llm.storyteller.service.StoryExportService;
import nl.llm.storyteller.service.StorySessionService;
import nl.llm.storyteller.service.SummaryManager;
import nl.llm.storyteller.service.SummaryPromptBuilder;
import nl.llm.storyteller.service.TurnManager;
import nl.llm.storyteller.service.TurnStateStore;
import nl.llm.storyteller.service.ValidationPromptBuilder;
import nl.llm.storyteller.service.LlmBackendGuard;
import org.jline.reader.EndOfFileException;
import org.jline.reader.Binding;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AssistantApp {
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
    private static final int DISPLAY_MARGIN = 2;
    private static final int MIN_CONTENT_WIDTH = 20;
    private static final Pattern LIST_PREFIX_PATTERN = Pattern.compile("^(\\s*(?:[-*]|\\d+\\.)\\s+)(.*)$");
    private AssistantApp() {
    }

    static void main() throws IOException {
        AppContext context = createAppContext();

        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            PrintWriter output = terminal.writer();
            LineReader reader = createReader(terminal, output, context);
            printBanner(terminal, output, context.config());
            runChatLoop(reader, terminal, output, context);
        } finally {
            context.summaryManager().shutdown();
            context.recentSummaryManager().shutdown();
            context.canonicalStateManager().shutdown();
        }
    }

    private static AppContext createAppContext() {
        AppConfig config = AppConfig.load();
        HistoryStore historyStore = new HistoryStore(config.historyFile(), config.legacyHistoryFile());
        PromptResourceLoader promptResourceLoader = new PromptResourceLoader(config);
        PromptTemplateService promptTemplateService = new PromptTemplateService(promptResourceLoader);
        StoryChatPromptBuilder storyChatPromptBuilder = new StoryChatPromptBuilder(
            promptResourceLoader,
            promptTemplateService
        );
        ValidationPromptBuilder validationPromptBuilder = new ValidationPromptBuilder(
            promptResourceLoader,
            promptTemplateService
        );
        SummaryPromptBuilder summaryPromptBuilder = new SummaryPromptBuilder(
            promptResourceLoader,
            promptTemplateService
        );
        RecentSummaryPromptBuilder recentSummaryPromptBuilder = new RecentSummaryPromptBuilder(
            promptResourceLoader,
            promptTemplateService
        );
        CanonicalStatePromptBuilder canonicalStatePromptBuilder = new CanonicalStatePromptBuilder(
            promptResourceLoader,
            promptTemplateService
        );
        LMStudioClient chatDelegate = new LMStudioClient(
            config.lmStudioUrl(),
            config.chatModel(),
            config.hideReasoningBlocks()
        );
        LMStudioClient validatorDelegate = new LMStudioClient(
            config.lmStudioUrl(),
            config.validatorModel(),
            config.hideReasoningBlocks()
        );
        ResilientChatClient chatClient = new ResilientChatClient(
            chatDelegate,
            new LlmBackendGuard("Chat backend", config.chatFailureThreshold(), config.chatCooldownSeconds())
        );
        ResilientChatClient validatorClient = new ResilientChatClient(
            validatorDelegate,
            new LlmBackendGuard("Validation backend", config.validationFailureThreshold(), config.validationCooldownSeconds())
        );
        ResilientChatClient backgroundClient = new ResilientChatClient(
            chatDelegate,
            new LlmBackendGuard("Background memory backend", config.backgroundFailureThreshold(), config.backgroundCooldownSeconds())
        );
        SummaryManager summaryManager = new SummaryManager(
            historyStore, backgroundClient, config, promptResourceLoader, promptTemplateService, summaryPromptBuilder
        );
        RecentSummaryManager recentSummaryManager = new RecentSummaryManager(
            historyStore, backgroundClient, config, promptResourceLoader, promptTemplateService, recentSummaryPromptBuilder
        );
        CanonicalStateManager canonicalStateManager = new CanonicalStateManager(
            historyStore,
            backgroundClient,
            config,
            promptResourceLoader,
            promptTemplateService,
            canonicalStatePromptBuilder
        );
        TurnManager turnManager = new TurnManager(
            config,
            promptResourceLoader,
            promptTemplateService,
            new GameModeDefinitionParser(),
            new TurnStateStore(config.turnStateFile())
        );
        PromptAssemblyService promptAssemblyService = new PromptAssemblyService(
            historyStore,
            summaryManager,
            recentSummaryManager,
            canonicalStateManager,
            turnManager,
            storyChatPromptBuilder,
            validationPromptBuilder
        );
        StorySessionService storySessionService = new StorySessionService(
            config,
            historyStore,
            chatClient,
            new ResponseGuard(validatorClient, config),
            summaryManager,
            recentSummaryManager,
            canonicalStateManager,
            promptAssemblyService,
            promptResourceLoader
        );
        return new AppContext(
            config,
            summaryManager,
            recentSummaryManager,
            canonicalStateManager,
            storySessionService,
            new StoryExportService(historyStore, config.baseDir())
        );
    }

    private static LineReader createReader(Terminal terminal, PrintWriter output, AppContext context) {
        LineReader reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .appName(APP_NAME)
            .build();

        registerContinueStoryShortcut(reader, context.config());
        registerResetShortcut(reader, terminal, output, context);
        registerUndoShortcut(reader, terminal, output, context);
        registerLastTurnShortcut(reader, terminal, output, context);
        return reader;
    }

    private static void registerContinueStoryShortcut(LineReader reader, AppConfig config) {
        reader.getWidgets().put(CONTINUE_STORY_WIDGET, () -> {
            reader.getBuffer().clear();
            reader.getBuffer().write(config.continueStoryCommand());
            reader.callWidget(LineReader.ACCEPT_LINE);
            return true;
        });

        Reference binding = new Reference(CONTINUE_STORY_WIDGET);
        bindShortcut(reader, LineReader.MAIN, binding, 'G', 'g');
        bindShortcut(reader, LineReader.EMACS, binding, 'G', 'g');
        bindShortcut(reader, LineReader.VIINS, binding, 'G', 'g');
    }

    private static void registerResetShortcut(LineReader reader, Terminal terminal, PrintWriter output, AppContext context) {
        reader.getWidgets().put(RESET_WIDGET, () -> {
            try {
                reader.getBuffer().clear();
                printMessage(terminal, output, context.config().resetSentText());
                context.storySessionService().handleUserTurn(context.config().resetStoryCommand());
                return true;
            } catch (InterruptedException ex) {
                printError(terminal, output, context.config().lmStudioRequestErrorText(), ex.getMessage());
                Thread.currentThread().interrupt();
                return true;
            } catch (IOException ex) {
                printError(terminal, output, context.config().lmStudioRequestErrorText(), ex.getMessage());
                return true;
            } catch (RuntimeException ex) {
                printError(terminal, output, context.config().processHistoryErrorText(), ex.getMessage());
                return true;
            }
        });

        Reference binding = new Reference(RESET_WIDGET);
        bindShortcut(reader, LineReader.MAIN, binding, 'W', 'w');
        bindShortcut(reader, LineReader.EMACS, binding, 'W', 'w');
        bindShortcut(reader, LineReader.VIINS, binding, 'W', 'w');
    }

    private static void registerUndoShortcut(LineReader reader, Terminal terminal, PrintWriter output, AppContext context) {
        reader.getWidgets().put(UNDO_WIDGET, () -> {
            try {
                printMessage(terminal, output, context.config().undoSentText());
                StorySessionService.UndoResult result = context.storySessionService().undoLastTurnAndReset();
                reader.getBuffer().clear();

                if (!result.hasRestoredUserInput()) {
                    printMessage(terminal, output, context.config().noStoryTurnToUndoText());
                    return true;
                }

                printLastPersistedTurn(terminal, output, context.config(), context.storySessionService().loadLastTurn());
                printMessage(terminal, output, context.config().undoRestoredText());
                reader.getBuffer().write(result.restoredUserInput());
                return true;
            } catch (InterruptedException ex) {
                printError(terminal, output, context.config().lmStudioRequestErrorText(), ex.getMessage());
                Thread.currentThread().interrupt();
                return true;
            } catch (IOException ex) {
                printError(terminal, output, context.config().lmStudioRequestErrorText(), ex.getMessage());
                return true;
            } catch (RuntimeException ex) {
                printError(terminal, output, context.config().processHistoryErrorText(), ex.getMessage());
                return true;
            }
        });

        Reference binding = new Reference(UNDO_WIDGET);
        bindShortcut(reader, LineReader.MAIN, binding, 'U', 'u');
        bindShortcut(reader, LineReader.EMACS, binding, 'U', 'u');
        bindShortcut(reader, LineReader.VIINS, binding, 'U', 'u');
    }

    private static void registerLastTurnShortcut(LineReader reader, Terminal terminal, PrintWriter output, AppContext context) {
        reader.getWidgets().put(LAST_TURN_WIDGET, () -> {
            printLastPersistedTurn(terminal, output, context.config(), context.storySessionService().loadLastTurn());
            return true;
        });

        Reference binding = new Reference(LAST_TURN_WIDGET);
        bindShortcut(reader, LineReader.MAIN, binding, 'L', 'l');
        bindShortcut(reader, LineReader.EMACS, binding, 'L', 'l');
        bindShortcut(reader, LineReader.VIINS, binding, 'L', 'l');
    }

    private static void bindShortcut(LineReader reader, String keyMapName, Reference binding, char ctrlKey, char altKey) {
        KeyMap<Binding> keyMap = reader.getKeyMaps().get(keyMapName);
        if (keyMap != null) {
            keyMap.bind(binding, KeyMap.ctrl(ctrlKey));
            keyMap.bind(binding, KeyMap.alt(altKey));
        }
    }

    private static void printBanner(Terminal terminal, PrintWriter output, AppConfig config) {
        output.println(formatForDisplay(
            """
                %s
                - %s
                - %s
                - %s
                - %s
                - %s
                - %s
                """.formatted(
                config.bannerStartText(),
                config.commandHelpText(),
                config.shortcutContinueHint(),
                config.shortcutResetHint(),
                config.shortcutUndoHint(),
                config.shortcutLastTurnHint(),
                config.macHint()
            ).strip(),
            terminal
        ));
        output.println();
        output.flush();
    }

    private static void printLastPersistedTurn(Terminal terminal, PrintWriter output, AppConfig config, HistoryStore.LastTurn lastTurn) {
        if (!lastTurn.isPresent()) {
            printMessage(terminal, output, config.noLastTurnText());
            return;
        }

        String lastTurnText = config.lastTurnTemplate().formatted(lastTurn.userInput(), lastTurn.assistantResponse()).strip();
        printMessage(terminal, output, lastTurnText);
    }

    private static void runChatLoop(LineReader reader, Terminal terminal, PrintWriter output, AppContext context) {
        String userInput;
        while ((userInput = readUserInput(reader)) != null) {
            if (shouldExit(userInput)) {
                return;
            }
            if (handleCommand(userInput, terminal, output, context)) {
                continue;
            }
            if (!userInput.isEmpty() && !handleUserTurn(userInput, terminal, output, context)) {
                return;
            }
        }
    }

    private static String readUserInput(LineReader reader) {
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

    private static boolean shouldExit(String userInput) {
        return EXIT_COMMAND.equalsIgnoreCase(userInput) || QUIT_COMMAND.equalsIgnoreCase(userInput);
    }

    private static boolean handleCommand(String userInput, Terminal terminal, PrintWriter output, AppContext context) {
        if (!userInput.startsWith(EXPORT_COMMAND)) {
            return false;
        }

        StoryExportService.ExportMode exportMode = parseExportMode(userInput);
        if (exportMode == null) {
            printError(
                terminal,
                output,
                "Export command error",
                "Use /export, /export -intro, /export -clean, or /export -all."
            );
            return true;
        }

        try {
            var path = context.storyExportService().export(exportMode);
            printMessage(terminal, output, "Story exported to " + path.getFileName());
        } catch (RuntimeException ex) {
            printError(terminal, output, context.config().processHistoryErrorText(), ex.getMessage());
        }
        return true;
    }

    private static StoryExportService.ExportMode parseExportMode(String userInput) {
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

    private static boolean handleUserTurn(String userInput, Terminal terminal, PrintWriter output, AppContext context) {
        try {
            String response = context.storySessionService().handleUserTurn(userInput);
            printMessage(terminal, output, response);
            return true;
        } catch (InterruptedException ex) {
            printError(terminal, output, context.config().lmStudioRequestErrorText(), ex.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException ex) {
            printError(terminal, output, context.config().lmStudioRequestErrorText(), ex.getMessage());
            return true;
        } catch (RuntimeException ex) {
            printError(terminal, output, context.config().processHistoryErrorText(), ex.getMessage());
            return true;
        }
    }

    private static void printMessage(Terminal terminal, PrintWriter output, String message) {
        output.printf("%n%s%n%n", formatForDisplay(message, terminal));
        output.flush();
    }

    private static void printError(Terminal terminal, PrintWriter output, String label, String message) {
        output.printf("%n%s%n%n", formatForDisplay(label + ": " + message, terminal));
        output.flush();
    }

    private static String formatForDisplay(String text, Terminal terminal) {
        int contentWidth = resolveContentWidth(terminal);
        String normalized = text.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        StringBuilder formatted = new StringBuilder();
        boolean inCodeBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String outputLine = line;

            if (line.stripLeading().startsWith("```")) {
                inCodeBlock = !inCodeBlock;
            } else if (!inCodeBlock && !line.isBlank()) {
                outputLine = wrapLine(line, contentWidth);
            }

            appendFormattedLine(formatted, outputLine, i > 0);
        }

        return formatted.toString();
    }

    private static void appendFormattedLine(StringBuilder formatted, String line, boolean prependNewline) {
        if (prependNewline) {
            formatted.append('\n');
        }
        formatted.append(line);
    }

    private static int resolveContentWidth(Terminal terminal) {
        int terminalWidth = terminal.getWidth() > 0 ? terminal.getWidth() : 80;
        return Math.max(MIN_CONTENT_WIDTH, terminalWidth - (DISPLAY_MARGIN * 2));
    }

    private static String wrapLine(String line, int contentWidth) {
        String margin = " ".repeat(DISPLAY_MARGIN);
        Matcher matcher = LIST_PREFIX_PATTERN.matcher(line);
        if (matcher.matches()) {
            String listPrefix = matcher.group(1);
            return wrapWords(matcher.group(2), contentWidth, margin + listPrefix, margin + " ".repeat(listPrefix.length()));
        }
        return wrapWords(line.strip(), contentWidth, margin, margin);
    }

    private static String wrapWords(String text, int contentWidth, String firstIndent, String continuationIndent) {
        String[] words = text.trim().split("\\s+");
        StringBuilder wrapped = new StringBuilder();
        String currentIndent = firstIndent;
        int currentLineLength = currentIndent.length();
        wrapped.append(currentIndent);

        for (String word : words) {
            int additionalLength = currentLineLength > currentIndent.length() ? 1 + word.length() : word.length();
            if (currentLineLength > currentIndent.length()
                && currentLineLength + additionalLength > currentIndent.length() + contentWidth) {
                wrapped.append('\n').append(continuationIndent).append(word);
                currentIndent = continuationIndent;
                currentLineLength = continuationIndent.length() + word.length();
                continue;
            }

            if (currentLineLength > currentIndent.length()) {
                wrapped.append(' ');
                currentLineLength++;
            }
            wrapped.append(word);
            currentLineLength += word.length();
        }

        return wrapped.toString();
    }

    private record AppContext(
        AppConfig config,
        SummaryManager summaryManager,
        RecentSummaryManager recentSummaryManager,
        CanonicalStateManager canonicalStateManager,
        StorySessionService storySessionService,
        StoryExportService storyExportService
    ) {}
}
