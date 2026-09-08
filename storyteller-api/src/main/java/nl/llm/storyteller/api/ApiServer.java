package nl.llm.storyteller.api;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import io.javalin.community.ssl.SslPlugin;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinJte;
import nl.llm.storyteller.api.bundle.SessionBundleService;
import nl.llm.storyteller.api.http.ApiErrorHandler;
import nl.llm.storyteller.api.http.SessionController;
import nl.llm.storyteller.api.http.StoryController;
import nl.llm.storyteller.db.Database;
import nl.llm.storyteller.db.JdbcSessionBundleRepository;
import nl.llm.storyteller.db.JdbcSessionPromptRepository;
import nl.llm.storyteller.db.JdbcSessionMemoryRepository;
import nl.llm.storyteller.db.JdbcSessionRepository;
import nl.llm.storyteller.db.JdbcSessionSettingsRepository;
import nl.llm.storyteller.db.JdbcStoryRepository;
import nl.llm.storyteller.db.SchemaInitializer;
import nl.llm.storyteller.db.SessionPrompts;
import nl.llm.storyteller.api.session.SessionCookieService;
import nl.llm.storyteller.api.session.SessionSettingsService;
import nl.llm.storyteller.api.session.SessionService;
import nl.llm.storyteller.api.story.StoryTurnService;
import nl.llm.storyteller.api.story.SessionDerivedStateService;
import nl.llm.storyteller.api.story.SessionKnowledgeGraphService;
import nl.llm.storyteller.api.story.SessionMemoryService;
import nl.llm.storyteller.api.web.WebController;
import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.graph.KnowledgeGraphValidator;
import nl.llm.storyteller.core.graph.PredicateCatalog;
import nl.llm.storyteller.core.service.ChatClient;
import nl.llm.storyteller.core.service.LmStudioNativeChatClient;
import nl.llm.storyteller.core.service.OpenAiCompatibleHttpClient;
import nl.llm.storyteller.core.service.PromptResourceLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ApiServer implements AutoCloseable {
  private final ApiConfig config;
  private final Javalin server;
  private final SessionDerivedStateService derivedStateService;

  private ApiServer(ApiConfig config, Javalin server, SessionDerivedStateService derivedStateService) {
    this.config = config;
    this.server = server;
    this.derivedStateService = derivedStateService;
  }

  public static ApiServer create(ApiConfig config) {
    AppConfig coreConfig = AppConfig.load();
    ChatClient chatClient = openAiClient(coreConfig, coreConfig.chatModel());
    ChatClient derivedStateClient = derivedStateClient(coreConfig);
    return create(
      config,
      coreConfig,
      chatClient,
      derivedStateClient(coreConfig, coreConfig.validatorModel()),
      derivedStateClient,
      derivedStateClient
    );
  }

  static ApiServer create(
    ApiConfig config,
    AppConfig coreConfig,
    ChatClient chatClient,
    ChatClient validationClient
  ) {
    ChatClient unavailableBackgroundClient = (_, _, _) -> {
      throw new IOException("Background memory client is not configured for this test server.");
    };
    return create(
      config, coreConfig, chatClient, validationClient, unavailableBackgroundClient, unavailableBackgroundClient
    );
  }

  static ApiServer create(
    ApiConfig config,
    AppConfig coreConfig,
    ChatClient chatClient,
    ChatClient validationClient,
    ChatClient backgroundClient
  ) {
    return create(config, coreConfig, chatClient, validationClient, backgroundClient, backgroundClient);
  }

  private static ApiServer create(
    ApiConfig config,
    AppConfig coreConfig,
    ChatClient chatClient,
    ChatClient validationClient,
    ChatClient backgroundClient,
    ChatClient graphClient
  ) {
    createDatabaseDirectory(config.databasePath());
    Database database = new Database(
      config.databaseUrl(),
      config.databaseUsername(),
      config.databasePassword()
    );
    new SchemaInitializer(database).initialize();

    PromptResourceLoader promptResources = new PromptResourceLoader(coreConfig);
    SessionPrompts defaultPrompts = new SessionPrompts(
      promptResources.loadSystemPrompt(),
      promptResources.loadFixedProtagonists(),
      promptResources.loadRulesPrompt()
    );
    JdbcSessionPromptRepository promptRepository = new JdbcSessionPromptRepository(database);
    promptRepository.initializeMissing(defaultPrompts);
    SessionService sessionService = new SessionService(
      new JdbcSessionRepository(database),
      config.sessionInactivityTimeout(),
      defaultPrompts
    );
    sessionService.deleteExpired();
    SessionCookieService cookieService = new SessionCookieService(
      config.sessionInactivityTimeout(), config.tls().enabled()
    );
    SessionController sessionController = new SessionController(sessionService, cookieService);
    JdbcStoryRepository storyRepository = new JdbcStoryRepository(database);
    JdbcSessionSettingsRepository settingsRepository = new JdbcSessionSettingsRepository(database);
    KnowledgeGraphValidator graphValidator = new KnowledgeGraphValidator(PredicateCatalog.load(coreConfig.baseDir()));
    JdbcSessionMemoryRepository memoryRepository = new JdbcSessionMemoryRepository(database);
    SessionMemoryService memoryService = new SessionMemoryService(
      memoryRepository, settingsRepository, coreConfig, backgroundClient
    );
    SessionDerivedStateService derivedStateService = new SessionDerivedStateService(
      memoryService,
      new SessionKnowledgeGraphService(database, coreConfig, graphClient)
    );
    StoryTurnService storyTurnService = new StoryTurnService(
      storyRepository, settingsRepository, memoryRepository, derivedStateService,
      coreConfig, chatClient, validationClient
    );
    StoryController storyController = new StoryController(
      sessionService,
      storyTurnService
    );
    SessionBundleService bundleService = new SessionBundleService(
      new JdbcSessionBundleRepository(database), config.sessionInactivityTimeout(), defaultPrompts
    );
    WebController webController = new WebController(
      sessionService,
      cookieService,
      storyRepository,
      storyTurnService,
      bundleService,
      new SessionSettingsService(settingsRepository, graphValidator),
      memoryRepository
    );
    ApiTlsMaterial tlsMaterial = config.tls().enabled() ? ApiTlsMaterial.prepare(config.tls()) : null;
    Javalin server = Javalin.create(javalinConfig -> {
      javalinConfig.startup.showJavalinBanner = false;
      if (tlsMaterial != null) {
        javalinConfig.registerPlugin(new SslPlugin(ssl -> {
          ssl.host = config.host();
          ssl.insecure = true;
          ssl.insecurePort = config.port();
          ssl.secure = true;
          ssl.securePort = config.tls().port();
          ssl.redirect = false;
          ssl.keystoreFromPath(tlsMaterial.keyStore().toString(), "");
        }));
      }
      javalinConfig.fileRenderer(new JavalinJte(TemplateEngine.createPrecompiled(ContentType.Html)));
      javalinConfig.staticFiles.add("/public", Location.CLASSPATH);
      if (tlsMaterial != null) {
        javalinConfig.routes.get("/storyteller-ca.crt", context -> {
          context.contentType("application/x-x509-ca-cert");
          context.header("Content-Disposition", "attachment; filename=storyteller-ca.crt");
          context.result(Files.newInputStream(tlsMaterial.caCertificate()));
        });
      }
      sessionController.register(javalinConfig);
      storyController.register(javalinConfig);
      webController.register(javalinConfig);
      ApiErrorHandler.register(javalinConfig);
    });
    return new ApiServer(config, server, derivedStateService);
  }

  private static ChatClient openAiClient(AppConfig config, String model) {
    return new OpenAiCompatibleHttpClient(
      config.openAiCompatibleUrl(), model, config.hideReasoningBlocks(), config.openAiCompatibleApiKey()
    );
  }

  private static ChatClient derivedStateClient(AppConfig config) {
    return derivedStateClient(config, config.chatModel());
  }

  private static ChatClient derivedStateClient(AppConfig config, String model) {
    if ("lmstudio-native".equalsIgnoreCase(config.graphGenerationTransport())) {
      return new LmStudioNativeChatClient(
        config.openAiCompatibleUrl(), model, config.openAiCompatibleApiKey(),
        nl.llm.storyteller.core.service.ChatRequestMetrics.NONE
      );
    }
    return openAiClient(config, model);
  }

  private static void createDatabaseDirectory(Path databasePath) {
    Path parent = databasePath.toAbsolutePath().normalize().getParent();
    if (parent == null) {
      return;
    }
    try {
      Files.createDirectories(parent);
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not create the API database directory.", ex);
    }
  }

  public void start() {
    if (config.tls().enabled()) {
      server.start();
    } else {
      server.start(config.host(), config.port());
    }
  }

  public int port() {
    return config.tls().enabled() ? config.tls().port() : server.port();
  }

  @Override
  public void close() {
    server.stop();
    derivedStateService.close();
  }
}
