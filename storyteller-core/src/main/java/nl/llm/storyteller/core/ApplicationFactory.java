package nl.llm.storyteller.core;

import nl.llm.storyteller.core.graph.service.ReadOnlyKnowledgeGraphService;
import nl.llm.storyteller.core.graph.service.KnowledgeGraphInitializer;
import nl.llm.storyteller.core.graph.service.KnowledgeGraphGenerator;
import nl.llm.storyteller.core.graph.service.KnowledgeGraphManagementService;
import nl.llm.storyteller.core.graph.turnbasedservice.TurnBasedKnowledgeGraphService;
import nl.llm.storyteller.core.graph.turnbasedservice.KnowledgeGraphUpdateObserver;
import nl.llm.storyteller.core.graph.KnowledgeGraphValidator;
import nl.llm.storyteller.core.graph.PredicateCatalog;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphStore;
import nl.llm.storyteller.core.service.CanonicalStateManager;
import nl.llm.storyteller.core.service.CanonicalStatePromptBuilder;
import nl.llm.storyteller.core.service.ChatRequestMetrics;
import nl.llm.storyteller.core.service.ChatClient;
import nl.llm.storyteller.core.service.DerivedMemoryTaskQueue;
import nl.llm.storyteller.core.service.GameModeDefinitionParser;
import nl.llm.storyteller.core.service.HistoryStore;
import nl.llm.storyteller.core.service.OpenAiCompatibleHttpClient;
import nl.llm.storyteller.core.service.LlmBackendGuard;
import nl.llm.storyteller.core.graph.service.KnowledgeGraphFillService;
import nl.llm.storyteller.core.service.ManagedLlamaServer;
import nl.llm.storyteller.core.service.ManagedMlxServer;
import nl.llm.storyteller.core.service.PromptAssemblyService;
import nl.llm.storyteller.core.service.PromptResourceLoader;
import nl.llm.storyteller.core.service.PromptTemplateService;
import nl.llm.storyteller.core.service.RecentSummaryManager;
import nl.llm.storyteller.core.service.RecentSummaryPromptBuilder;
import nl.llm.storyteller.core.service.ResilientChatClient;
import nl.llm.storyteller.core.service.ResponseGuard;
import nl.llm.storyteller.core.service.StoryChatPromptBuilder;
import nl.llm.storyteller.core.service.StoryExportService;
import nl.llm.storyteller.core.service.StorySessionService;
import nl.llm.storyteller.core.service.StoryTurnObserver;
import nl.llm.storyteller.core.service.SummaryManager;
import nl.llm.storyteller.core.service.SummaryPromptBuilder;
import nl.llm.storyteller.core.service.TurnManager;
import nl.llm.storyteller.core.service.TurnStateStore;
import nl.llm.storyteller.core.service.ValidationPromptBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class ApplicationFactory {
  private ApplicationFactory() {
  }

  public static ApplicationContext create() {
    return create(nl.llm.storyteller.core.config.AppConfig.load(), ChatRequestMetrics.NONE);
  }

  public static ApplicationContext create(nl.llm.storyteller.core.config.AppConfig config) {
    return create(config, ChatRequestMetrics.NONE);
  }

  public static ApplicationContext create(
    nl.llm.storyteller.core.config.AppConfig config,
    ChatRequestMetrics metrics
  ) {
    return create(config, metrics, StoryTurnObserver.NONE, KnowledgeGraphUpdateObserver.NONE);
  }

  public static ApplicationContext create(
    nl.llm.storyteller.core.config.AppConfig config,
    ChatRequestMetrics metrics,
    StoryTurnObserver turnObserver
  ) {
    return create(config, metrics, turnObserver, KnowledgeGraphUpdateObserver.NONE);
  }

  public static ApplicationContext create(
    nl.llm.storyteller.core.config.AppConfig config,
    ChatRequestMetrics metrics,
    StoryTurnObserver turnObserver,
    KnowledgeGraphUpdateObserver graphObserver
  ) {
    HistoryStore historyStore = new HistoryStore(config.historyFile(), config.legacyHistoryFile());
    PromptResourceLoader promptResourceLoader = new PromptResourceLoader(config);
    PromptTemplateService promptTemplateService = new PromptTemplateService(promptResourceLoader);
    StoryChatPromptBuilder storyChatPromptBuilder = new StoryChatPromptBuilder(
      promptResourceLoader, promptTemplateService
    );
    ValidationPromptBuilder validationPromptBuilder = new ValidationPromptBuilder(
      promptResourceLoader, promptTemplateService
    );
    SummaryPromptBuilder summaryPromptBuilder = new SummaryPromptBuilder(
      promptResourceLoader, promptTemplateService
    );
    RecentSummaryPromptBuilder recentSummaryPromptBuilder = new RecentSummaryPromptBuilder(
      promptResourceLoader, promptTemplateService
    );
    CanonicalStatePromptBuilder canonicalStatePromptBuilder = new CanonicalStatePromptBuilder(
      promptResourceLoader, promptTemplateService
    );
    ManagedLlamaServer managedLlamaServer = startManagedLlamaServerIfConfigured(config);
    ManagedMlxServer managedMlxServer = startManagedMlxServerIfConfigured(config);
    String backendUrl = resolveBackendUrl(config, managedLlamaServer, managedMlxServer);
    OpenAiCompatibleHttpClient chatDelegate = new OpenAiCompatibleHttpClient(
      backendUrl, config.chatModel(), config.hideReasoningBlocks(), config.openAiCompatibleApiKey(), metrics, "generation"
    );
    ChatClient validatorDelegate = config.validationEnabled()
      ? new OpenAiCompatibleHttpClient(
        backendUrl, config.validatorModel(), config.hideReasoningBlocks(), config.openAiCompatibleApiKey(), metrics, "validation"
      )
      : (_, _, _) -> {
        throw new IllegalStateException("Validation client is disabled by validation.enabled=false.");
      };
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
    DerivedMemoryTaskQueue derivedMemoryTaskQueue = new DerivedMemoryTaskQueue();
    SummaryManager summaryManager = new SummaryManager(
      historyStore, backgroundClient, config, promptResourceLoader, promptTemplateService, summaryPromptBuilder,
      derivedMemoryTaskQueue
    );
    RecentSummaryManager recentSummaryManager = new RecentSummaryManager(
      historyStore, backgroundClient, config, promptResourceLoader, promptTemplateService, recentSummaryPromptBuilder,
      derivedMemoryTaskQueue
    );
    CanonicalStateManager canonicalStateManager = new CanonicalStateManager(
      historyStore, backgroundClient, config, promptResourceLoader, promptTemplateService, canonicalStatePromptBuilder,
      derivedMemoryTaskQueue
    );
    TurnManager turnManager = new TurnManager(
      config,
      promptResourceLoader,
      promptTemplateService,
      new GameModeDefinitionParser(),
      new TurnStateStore(config.turnStateFile())
    );
    PredicateCatalog predicateCatalog = PredicateCatalog.load(config.baseDir());
    KnowledgeGraphStore knowledgeGraphStore = new KnowledgeGraphStore(
      config.knowledgeGraphFile(), new KnowledgeGraphValidator(predicateCatalog)
    );
    ReadOnlyKnowledgeGraphService knowledgeGraphService = new ReadOnlyKnowledgeGraphService(
      knowledgeGraphStore, predicateCatalog
    );
    TurnBasedKnowledgeGraphService turnBasedKnowledgeGraphService = new TurnBasedKnowledgeGraphService(
      historyStore,
      backgroundClient,
      knowledgeGraphStore,
      knowledgeGraphService,
      predicateCatalog,
      derivedMemoryTaskQueue,
      config.graphTurnBasedBatchTurns(),
      config.summaryOptions(),
      config.summaryRequestTimeoutSeconds(),
      graphObserver
    );
    PromptAssemblyService promptAssemblyService = new PromptAssemblyService(
      historyStore,
      summaryManager,
      recentSummaryManager,
      canonicalStateManager,
      turnManager,
      storyChatPromptBuilder,
      validationPromptBuilder,
      config.graphEnabled() ? knowledgeGraphService : null
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
      promptResourceLoader,
      config.graphEnabled() ? turnBasedKnowledgeGraphService : null,
      turnObserver
    );
    return new ApplicationContext(
      config,
      derivedMemoryTaskQueue,
      storySessionService,
      new StoryExportService(historyStore, config.baseDir()),
      knowledgeGraphService,
      new KnowledgeGraphInitializer(knowledgeGraphStore, knowledgeGraphService),
      new KnowledgeGraphFillService(
        promptResourceLoader,
        new KnowledgeGraphGenerator(
          backgroundClient,
          knowledgeGraphStore,
          knowledgeGraphService,
          config.summaryOptions(),
          config.summaryRequestTimeoutSeconds(),
          predicateCatalog
        )
      ),
      new KnowledgeGraphManagementService(knowledgeGraphStore, knowledgeGraphService),
      managedLlamaServer,
      managedMlxServer
    );
  }

  private static String resolveBackendUrl(
    nl.llm.storyteller.core.config.AppConfig config,
    ManagedLlamaServer managedLlamaServer,
    ManagedMlxServer managedMlxServer
  ) {
    if (managedLlamaServer != null) {
      return managedLlamaServer.chatCompletionsUrl();
    }
    if (managedMlxServer != null) {
      return managedMlxServer.chatCompletionsUrl();
    }
    return config.openAiCompatibleUrl();
  }

  private static ManagedLlamaServer startManagedLlamaServerIfConfigured(nl.llm.storyteller.core.config.AppConfig config) {
    if (!config.usesManagedLlamaServer()) {
      return null;
    }
    try {
      return ManagedLlamaServer.start(config.llamaServerConfig());
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not start managed llama-server", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while starting managed llama-server", ex);
    }
  }

  private static ManagedMlxServer startManagedMlxServerIfConfigured(nl.llm.storyteller.core.config.AppConfig config) {
    if (!config.usesManagedMlxServer()) {
      return null;
    }
    try {
      return ManagedMlxServer.start(config.mlxServerConfig());
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not start managed MLX server", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while starting managed MLX server", ex);
    }
  }
}
