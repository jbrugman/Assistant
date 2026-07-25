package nl.llm.storyteller;

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
    private static final String EXIT_COMMAND = "exit";
    private static final String QUIT_COMMAND = "quit";
    private static final String CONTINUE_STORY_WIDGET = "continue-story";
    private static final String RESET_WIDGET = "reset-behavior";
    private static final int DISPLAY_MARGIN = 2;
    private static final int MIN_CONTENT_WIDTH = 20;
    private static final String SYSTEM = "system";
    private static final Pattern LIST_PREFIX_PATTERN = Pattern.compile("^(\\s*(?:[-*]|\\d+\\.)\\s+)(.*)$");

    private AssistantApp() {
    }

    public static void main(String[] args) throws IOException {
        AppContext context = createAppContext();

        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            LineReader reader = createReader(terminal, context.config());
            PrintWriter output = terminal.writer();
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
        PromptLoader promptLoader = new PromptLoader(config);
        LMStudioClient client = new LMStudioClient(
            config.lmStudioUrl(),
            config.chatModel(),
            config.hideReasoningBlocks()
        );
        LMStudioClient validatorClient = new LMStudioClient(
            config.lmStudioUrl(),
            config.validatorModel(),
            config.hideReasoningBlocks()
        );
        SummaryManager summaryManager = new SummaryManager(historyStore, client, config, promptLoader);
        RecentSummaryManager recentSummaryManager = new RecentSummaryManager(historyStore, client, config, promptLoader);
        CanonicalStateManager canonicalStateManager = new CanonicalStateManager(historyStore, client, config, promptLoader);
        StorySessionService storySessionService = new StorySessionService(
            config,
            historyStore,
            client,
            new ResponseGuard(validatorClient, config),
            summaryManager,
            recentSummaryManager,
            canonicalStateManager,
            promptLoader
        );
        return new AppContext(
            config,
            summaryManager,
            recentSummaryManager,
            canonicalStateManager,
            storySessionService
        );
    }

    private static LineReader createReader(Terminal terminal, AppConfig config) {
        LineReader reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .appName(APP_NAME)
            .build();

        registerContinueStoryShortcut(reader, config);
        registerResetShortcut(reader, config);
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

    private static void registerResetShortcut(LineReader reader, AppConfig config) {
        reader.getWidgets().put(RESET_WIDGET, () -> {
            reader.getBuffer().clear();
            reader.getBuffer().write(config.resetStoryCommand());
            reader.callWidget(LineReader.ACCEPT_LINE);
            return true;
        });

        Reference binding = new Reference(RESET_WIDGET);
        bindShortcut(reader, LineReader.MAIN, binding, 'W', 'w');
        bindShortcut(reader, LineReader.EMACS, binding, 'W', 'w');
        bindShortcut(reader, LineReader.VIINS, binding, 'W', 'w');
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
            config.bannerStartText() + " "
                + config.shortcutContinueHint() + " "
                + config.shortcutResetHint(),
            terminal
        ));
        output.println();
        output.flush();
    }

    private static void runChatLoop(LineReader reader, Terminal terminal, PrintWriter output, AppContext context) {
        while (true) {
            String userInput = readUserInput(reader);
            if (userInput == null || shouldExit(userInput)) {
                return;
            }
            if (userInput.isEmpty()) {
                continue;
            }
            if (!handleUserTurn(userInput, terminal, output, context)) {
                return;
            }
        }
    }

    private static String readUserInput(LineReader reader) {
        while (true) {
            try {
                return reader.readLine("> ").trim();
            } catch (UserInterruptException ex) {
                // Let the user cancel the current line without exiting the app.
            } catch (EndOfFileException ex) {
                return null;
            }
        }
    }

    private static boolean shouldExit(String userInput) {
        return EXIT_COMMAND.equalsIgnoreCase(userInput) || QUIT_COMMAND.equalsIgnoreCase(userInput);
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
            if (line.stripLeading().startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                appendFormattedLine(formatted, line, i > 0);
                continue;
            }

            if (inCodeBlock || line.isBlank()) {
                appendFormattedLine(formatted, line, i > 0);
                continue;
            }

            appendFormattedLine(formatted, wrapLine(line, contentWidth), i > 0);
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
        StorySessionService storySessionService
    ) {}
}
