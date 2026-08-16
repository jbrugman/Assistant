package nl.llm.storyteller.core;

import nl.llm.storyteller.core.service.CanonicalStateManager;
import nl.llm.storyteller.core.service.CanonicalStatePromptBuilder;
import nl.llm.storyteller.core.service.DerivedMemoryTaskQueue;
import nl.llm.storyteller.core.service.GameModeDefinitionParser;
import nl.llm.storyteller.core.service.HistoryStore;
import nl.llm.storyteller.core.service.LMStudioClient;
import nl.llm.storyteller.core.service.LlmBackendGuard;
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
    LMStudioClient chatDelegate = new LMStudioClient(
      config.lmStudioUrl(), config.chatModel(), config.hideReasoningBlocks()
    );
    LMStudioClient validatorDelegate = new LMStudioClient(
      config.lmStudioUrl(), config.validatorModel(), config.hideReasoningBlocks()
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
      new StoryExportService(historyStore, config.baseDir())
    );
  }
}
