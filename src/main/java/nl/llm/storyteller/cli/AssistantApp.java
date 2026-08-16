package nl.llm.storyteller.cli;

import nl.llm.storyteller.core.ApplicationContext;
import nl.llm.storyteller.core.ApplicationFactory;
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
