package nl.llm.storyteller.cli;

import nl.llm.storyteller.core.ApplicationContext;
import nl.llm.storyteller.core.ApplicationFactory;
import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.graph.turnbasedservice.KnowledgeGraphUpdateObserver;
import nl.llm.storyteller.core.service.ChatRequestMetrics;
import nl.llm.storyteller.core.service.StoryTurnObserver;
import nl.llm.storyteller.db.JdbcApplicationStorageFactory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

public final class AssistantApp {
  private AssistantApp() {
  }

  static void main(String... args) throws IOException {
    CliArguments arguments = CliArguments.parse(args);
    AppConfig config = AppConfig.load();
    try (ApplicationContext context = ApplicationFactory.create(
           config,
           ChatRequestMetrics.NONE,
           StoryTurnObserver.NONE,
           KnowledgeGraphUpdateObserver.NONE,
           JdbcApplicationStorageFactory.create(config, arguments.sessionId())
         );
         Terminal terminal = TerminalBuilder.builder().system(true).build()) {
      new TerminalStoryteller(terminal, context).run();
    }
  }
}
