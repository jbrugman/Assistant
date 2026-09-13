package nl.llm.storyteller.api.story;

import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.graph.KnowledgeGraphValidator;
import nl.llm.storyteller.core.graph.PredicateCatalog;
import nl.llm.storyteller.core.graph.service.ReadOnlyKnowledgeGraphService;
import nl.llm.storyteller.core.graph.service.KnowledgeGraphFillService;
import nl.llm.storyteller.core.graph.service.KnowledgeGraphGenerator;
import nl.llm.storyteller.core.graph.service.KnowledgeGraphManagementService;
import nl.llm.storyteller.core.graph.service.KnowledgeGraphInitializer;
import nl.llm.storyteller.core.graph.turnbasedservice.TurnBasedKnowledgeGraphService;
import nl.llm.storyteller.core.service.ChatClient;
import nl.llm.storyteller.core.service.DerivedMemoryTaskQueue;
import nl.llm.storyteller.core.service.PromptLoader;
import nl.llm.storyteller.core.service.StoryPrompts;
import nl.llm.storyteller.db.Database;
import nl.llm.storyteller.db.JdbcKnowledgeGraphRepository;
import nl.llm.storyteller.db.JdbcSessionPromptRepository;
import nl.llm.storyteller.db.JdbcStoryHistory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;

public final class SessionKnowledgeGraphService implements AutoCloseable {
  private final Database database;
  private final AppConfig config;
  private final ChatClient client;
  private final PredicateCatalog predicates;
  private final KnowledgeGraphValidator validator;
  private final DerivedMemoryTaskQueue taskQueue = new DerivedMemoryTaskQueue();
  private final Set<String> forceNextUpdate = ConcurrentHashMap.newKeySet();

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
    ).startUpdateIfNeeded(forceNextUpdate.remove(sessionId));
  }

  public KnowledgeGraphManagementService.ResetResult resetTurnBasedItems(String sessionId)
    throws InterruptedException {
    try {
      KnowledgeGraphManagementService.ResetResult result = taskQueue.submitAndWait(() -> {
        var repository = repository(sessionId);
        var graphService = new ReadOnlyKnowledgeGraphService(repository, predicates);
        return new KnowledgeGraphManagementService(repository, graphService).resetTurnBasedItems();
      });
      forceNextUpdate.add(sessionId);
      return result;
    } catch (ExecutionException ex) {
      if (ex.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException("Could not reset turn-based knowledge graph data.", ex.getCause());
    }
  }

  public KnowledgeGraphGenerator.GenerationResult fillFromFixedProtagonists(String sessionId)
    throws IOException, InterruptedException {
    var repository = repository(sessionId);
    var graphService = new ReadOnlyKnowledgeGraphService(repository, predicates);
    var generator = new KnowledgeGraphGenerator(
      client,
      repository,
      graphService,
      config.summaryOptions(),
      config.summaryRequestTimeoutSeconds(),
      predicates
    );
    var promptRepository = new JdbcSessionPromptRepository(database);
    var promptResourceLoader = new PromptLoader(config, () -> {
      var prompts = promptRepository.load(sessionId);
      return new StoryPrompts(prompts.systemPrompt(), prompts.fixedProtagonists(), prompts.rules());
    });
    return new KnowledgeGraphFillService(promptResourceLoader, generator, taskQueue).fill();
  }

  public nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument generateEmpty(String sessionId)
    throws InterruptedException {
    try {
      return taskQueue.submitAndWait(() -> {
        var repository = repository(sessionId);
        var graphService = new ReadOnlyKnowledgeGraphService(repository, predicates);
        return new KnowledgeGraphInitializer(repository, graphService).generateEmpty();
      });
    } catch (ExecutionException ex) {
      if (ex.getCause() instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException("Could not generate an empty knowledge graph.", ex.getCause());
    }
  }

  private JdbcKnowledgeGraphRepository repository(String sessionId) {
    return new JdbcKnowledgeGraphRepository(database, sessionId, validator);
  }

  boolean awaitIdle() throws InterruptedException {
    return taskQueue.awaitIdle(5, TimeUnit.SECONDS);
  }

  @Override
  public void close() {
    taskQueue.close();
  }
}
