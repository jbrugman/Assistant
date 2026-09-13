package nl.llm.storyteller.db;

import nl.llm.storyteller.core.ApplicationStorage;
import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.graph.KnowledgeGraphValidator;
import nl.llm.storyteller.core.graph.PredicateCatalog;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphStore;
import nl.llm.storyteller.core.service.FileTextMemory;
import nl.llm.storyteller.core.service.HistoryStore;
import nl.llm.storyteller.core.service.PromptLoader;
import nl.llm.storyteller.core.service.TurnStateStore;
import nl.llm.storyteller.core.service.StoryPrompts;
import nl.llm.storyteller.db.bundle.SessionBundle;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class JdbcApplicationStorageFactory {
  public static final String CLI_SESSION_ID = "00000000-0000-0000-0000-000000000001";

  private JdbcApplicationStorageFactory() {
  }

  public static ApplicationStorage create(AppConfig config) {
    return create(config, null);
  }

  public static ApplicationStorage create(AppConfig config, String requestedSessionId) {
    Database database = new Database("jdbc:h2:file:" + config.databasePath(), "sa", "");
    new SchemaInitializer(database).initialize();
    PredicateCatalog predicates = PredicateCatalog.load(config.baseDir());
    KnowledgeGraphValidator graphValidator = new KnowledgeGraphValidator(predicates);
    JdbcSessionRepository sessions = new JdbcSessionRepository(database);
    String sessionId = requestedSessionId == null ? CLI_SESSION_ID : requestedSessionId;
    boolean sessionExists = sessions.findById(sessionId).isPresent();
    if (!sessionExists && requestedSessionId == null) {
      createSession(database, config, graphValidator);
    } else if (!sessionExists) {
      throw new IllegalArgumentException("Story session does not exist: " + sessionId);
    }

    ApplicationStorage storage = new ApplicationStorage(
      new JdbcStoryHistory(database, sessionId),
      new JdbcTextMemory(database, sessionId, JdbcTextMemory.Type.SUMMARY),
      new JdbcTextMemory(database, sessionId, JdbcTextMemory.Type.RECENT_SUMMARY),
      new JdbcTextMemory(database, sessionId, JdbcTextMemory.Type.CANONICAL_STATE),
      new JdbcTurnStateRepository(database, sessionId),
      new JdbcKnowledgeGraphRepository(database, sessionId, graphValidator),
      () -> {
        SessionPrompts prompts = new JdbcSessionPromptRepository(database).load(sessionId);
        return new StoryPrompts(prompts.systemPrompt(), prompts.fixedProtagonists(), prompts.rules());
      }
    );
    return storage;
  }

  private static void createSession(Database database, AppConfig config, KnowledgeGraphValidator graphValidator) {
    Instant now = Instant.now();
    PromptLoader resources = new PromptLoader(config);
    HistoryStore history = new HistoryStore(config.historyFile(), config.legacyHistoryFile());
    new JdbcSessionBundleRepository(database).create(
      new SessionRecord(CLI_SESSION_ID, "CLI story", now, now, now, now.plus(3650, ChronoUnit.DAYS), true),
      new SessionBundle(
        history.load(),
        new FileTextMemory(config.summaryFile()).load(),
        new FileTextMemory(config.recentSummaryFile()).load(),
        new FileTextMemory(config.canonicalStateFile()).load(),
        new TurnStateStore(config.turnStateFile()).load(),
        new KnowledgeGraphStore(config.knowledgeGraphFile(), graphValidator).load(),
        new SessionPrompts(resources.loadSystemPrompt(), resources.loadFixedProtagonists(), resources.loadRulesPrompt())
      )
    );
  }
}
