package nl.llm.storyteller.core;

import nl.llm.storyteller.core.service.CanonicalStateManager;
import nl.llm.storyteller.core.service.CanonicalStatePromptBuilder;
import nl.llm.storyteller.core.service.DerivedMemoryTaskQueue;
import nl.llm.storyteller.core.service.GameModeDefinitionParser;
import nl.llm.storyteller.core.service.HistoryStore;
import nl.llm.storyteller.core.service.OpenAiCompatibleHttpClient;
import nl.llm.storyteller.core.service.LlmBackendGuard;
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
    AppConfig config = AppConfig.load();
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
    String backendUrl = managedLlamaServer != null
      ? managedLlamaServer.chatCompletionsUrl()
      : managedMlxServer != null ? managedMlxServer.chatCompletionsUrl() : config.openAiCompatibleUrl();
    OpenAiCompatibleHttpClient chatDelegate = new OpenAiCompatibleHttpClient(
      backendUrl, config.chatModel(), config.hideReasoningBlocks()
    );
    OpenAiCompatibleHttpClient validatorDelegate = new OpenAiCompatibleHttpClient(
      backendUrl, config.validatorModel(), config.hideReasoningBlocks()
    );
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
    PromptAssemblyService promptAssemblyService = new PromptAssemblyService(
      historyStore,
      summaryManager,
      recentSummaryManager,
      canonicalStateManager,
      turnManager,
      storyChatPromptBuilder,
      validationPromptBuilder
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
      promptResourceLoader
    );
    return new ApplicationContext(
      config,
      derivedMemoryTaskQueue,
      storySessionService,
      new StoryExportService(historyStore, config.baseDir()),
      managedLlamaServer,
      managedMlxServer
    );
  }

  private static ManagedLlamaServer startManagedLlamaServerIfConfigured(AppConfig config) {
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

  private static ManagedMlxServer startManagedMlxServerIfConfigured(AppConfig config) {
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
