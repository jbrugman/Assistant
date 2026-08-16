package nl.llm.storyteller;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

public final class AssistantApp {
    private AssistantApp() {
    }

    static void main() throws IOException {
        try (ApplicationContext context = ApplicationFactory.create();
             Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            new TerminalStoryteller(terminal, context).run();
        }
    }
}
