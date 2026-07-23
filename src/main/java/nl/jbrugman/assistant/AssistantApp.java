package nl.jbrugman.assistant;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public final class AssistantApp {
    private static final String APP_NAME = "assistant";
    private static final String EXIT_COMMAND = "exit";
    private static final String QUIT_COMMAND = "quit";
    private static final String SYSTEM = "system";

    private AssistantApp() {
    }

    public static void main(String[] args) throws IOException {
        AppContext context = createAppContext();

        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            LineReader reader = createReader(terminal);
            PrintWriter output = terminal.writer();
            printBanner(output);
            runChatLoop(reader, output, context);
        } finally {
            context.summaryManager().shutdown();
            if (context.canonicalStateManager() != null) {
                context.canonicalStateManager().shutdown();
            }
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
        CanonicalStateManager canonicalStateManager = config.isStoryMode()
            ? new CanonicalStateManager(historyStore, client, config, promptLoader)
            : null;
        return new AppContext(
            config,
            historyStore,
            client,
            new ResponseGuard(validatorClient, config),
            new SummaryManager(historyStore, client, config, promptLoader),
            canonicalStateManager,
            promptLoader
        );
    }

    private static LineReader createReader(Terminal terminal) {
        return LineReaderBuilder.builder()
            .terminal(terminal)
            .appName(APP_NAME)
            .build();
    }

    private static void printBanner(PrintWriter output) {
        output.println("LM Studio wrapper gestart. Type 'exit' om te stoppen.\n");
        output.flush();
    }

    private static void runChatLoop(LineReader reader, PrintWriter output, AppContext context) {
        while (true) {
            String userInput = readUserInput(reader);
            if (userInput == null || shouldExit(userInput)) {
                return;
            }
            if (userInput.isEmpty()) {
                continue;
            }
            if (!handleUserTurn(userInput, output, context)) {
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

    private static boolean handleUserTurn(String userInput, PrintWriter output, AppContext context) {
        try {
            List<Message> messages = buildChatMessages(
                context.promptLoader().loadSystemPrompt(),
                context.canonicalStateManager() == null ? "" : context.canonicalStateManager().loadCanonicalState(),
                context.summaryManager().loadSummary(),
                context.historyStore().recentMessages(context.config().maxRecentTurns()),
                userInput
            );

            String draftResponse = context.client().chat(
                messages,
                context.config().chatOptions(),
                context.config().requestTimeoutSeconds()
            );
            String response = context.responseGuard().validate(
                context.promptLoader().loadRulesPrompt(),
                userInput,
                draftResponse
            );
            printMessage(output, response);

            context.historyStore().appendTurn(userInput, response);
            if (context.canonicalStateManager() != null) {
                context.canonicalStateManager().startUpdateIfNeeded();
            }
            context.summaryManager().startUpdateSummaryIfNeeded();
            return true;
        } catch (InterruptedException ex) {
            printError(output, "Fout bij LM Studio request", ex.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException ex) {
            printError(output, "Fout bij LM Studio request", ex.getMessage());
            return true;
        } catch (RuntimeException ex) {
            printError(output, "Fout bij verwerken van history of response", ex.getMessage());
            return true;
        }
    }

    private static void printMessage(PrintWriter output, String message) {
        output.printf("%n%s%n%n", message);
        output.flush();
    }

    private static void printError(PrintWriter output, String label, String message) {
        output.printf("%n%s: %s%n%n", label, message);
        output.flush();
    }

    private static List<Message> buildChatMessages(
        String systemPrompt,
        String canonicalState,
        String summary,
        List<Message> recentMessages,
        String userInput
    ) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(SYSTEM, systemPrompt));

        if (canonicalState != null && !canonicalState.isBlank()) {
            messages.add(
                new Message(
                  SYSTEM,
                    "Actuele canonieke verhaaltoestand. "
                        + "Gebruik dit als primaire bron voor bevestigde story-state zolang recentere berichten het niet tegenspreken.\n\n"
                        + canonicalState
                )
            );
        }

        if (summary != null && !summary.isBlank()) {
            messages.add(
                new Message(
                  SYSTEM,
                    "Langetermijngeheugen uit eerdere gesprekken. "
                        + "Gebruik dit alleen als achtergrond en geef prioriteit aan recente instructies.\n\n"
                        + summary
                )
            );
        }

        messages.addAll(recentMessages);
        messages.add(new Message("user", userInput));
        return messages;
    }

    private record AppContext(
        AppConfig config,
        HistoryStore historyStore,
        LMStudioClient client,
        ResponseGuard responseGuard,
        SummaryManager summaryManager,
        CanonicalStateManager canonicalStateManager,
        PromptLoader promptLoader
    ) {}
}
