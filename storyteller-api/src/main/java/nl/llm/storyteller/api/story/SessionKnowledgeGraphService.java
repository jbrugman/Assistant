package nl.llm.storyteller.api.story;

import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.graph.KnowledgeGraphValidator;
import nl.llm.storyteller.core.graph.PredicateCatalog;
import nl.llm.storyteller.core.graph.service.ReadOnlyKnowledgeGraphService;
import nl.llm.storyteller.core.graph.turnbasedservice.TurnBasedKnowledgeGraphService;
import nl.llm.storyteller.core.service.ChatClient;
import nl.llm.storyteller.core.service.DerivedMemoryTaskQueue;
import nl.llm.storyteller.db.Database;
import nl.llm.storyteller.db.JdbcKnowledgeGraphRepository;
import nl.llm.storyteller.db.JdbcStoryHistory;

import java.util.concurrent.TimeUnit;

public final class SessionKnowledgeGraphService implements AutoCloseable {
  private final Database database;
  private final AppConfig config;
  private final ChatClient client;
  private final PredicateCatalog predicates;
  private final KnowledgeGraphValidator validator;
  private final DerivedMemoryTaskQueue taskQueue = new DerivedMemoryTaskQueue();

  public SessionKnowledgeGraphService(Database database, AppConfig config, ChatClient client) {
    this.database = database;
    this.config = config;
    this.client = client;
    this.predicates = PredicateCatalog.load(config.baseDir());
    this.validator = new KnowledgeGraphValidator(predicates);
  }

  public void requestUpdate(String sessionId) {
    if (!config.graphEnabled()) {
      return;
    }
    var graphRepository = new JdbcKnowledgeGraphRepository(database, sessionId, validator);
    var graphService = new ReadOnlyKnowledgeGraphService(graphRepository, predicates);
    new TurnBasedKnowledgeGraphService(
      new JdbcStoryHistory(database, sessionId),
      client,
      graphRepository,
      graphService,
      predicates,
      taskQueue,
      config.graphTurnBasedBatchTurns(),
      config.summaryOptions(),
      config.summaryRequestTimeoutSeconds()
    ).startUpdateIfNeeded();
  }

  boolean awaitIdle(long timeout, TimeUnit unit) throws InterruptedException {
    return taskQueue.awaitIdle(timeout, unit);
  }

  @Override
  public void close() {
    taskQueue.close();
  }
}
