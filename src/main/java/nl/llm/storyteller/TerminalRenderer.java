package nl.llm.storyteller;

import nl.llm.storyteller.service.HistoryStore;
import org.jline.terminal.Terminal;

import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TerminalRenderer {
    private static final int DISPLAY_MARGIN = 2;
    private static final int MIN_CONTENT_WIDTH = 20;
    private static final Pattern LIST_PREFIX_PATTERN = Pattern.compile("^(\\s*(?:[-*]|\\d+\\.)\\s+)(.*)$");

    private final Terminal terminal;
    private final PrintWriter output;

    TerminalRenderer(Terminal terminal) {
        this.terminal = terminal;
        this.output = terminal.writer();
    }

    void printBanner(AppConfig config) {
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
            ).strip()
        ));
        output.println();
        output.flush();
    }

    void printLastPersistedTurn(AppConfig config, HistoryStore.LastTurn lastTurn) {
        if (!lastTurn.isPresent()) {
            printMessage(config.noLastTurnText());
            return;
        }
        printMessage(config.lastTurnTemplate().formatted(lastTurn.userInput(), lastTurn.assistantResponse()).strip());
    }

    void printMessage(String message) {
        output.printf("%n%s%n%n", formatForDisplay(message));
        output.flush();
    }

    void printError(String label, String message) {
        output.printf("%n%s%n%n", formatForDisplay(label + ": " + message));
        output.flush();
    }

    String formatForDisplay(String text) {
        return formatForDisplay(text, resolveContentWidth());
    }

    static String formatForDisplay(String text, int contentWidth) {
        String normalized = text.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        StringBuilder formatted = new StringBuilder();
        boolean inCodeBlock = false;

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String outputLine = line;
            if (line.stripLeading().startsWith("```")) {
                inCodeBlock = !inCodeBlock;
            } else if (!inCodeBlock && !line.isBlank()) {
                outputLine = wrapLine(line, contentWidth);
            }
            if (index > 0) {
                formatted.append('\n');
            }
            formatted.append(outputLine);
        }
        return formatted.toString();
    }

    private int resolveContentWidth() {
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
        StringBuilder wrapped = new StringBuilder(firstIndent);
        String currentIndent = firstIndent;
        int currentLineLength = currentIndent.length();

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
}
