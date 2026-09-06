package nl.llm.storyteller.api;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinJte;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import nl.llm.storyteller.api.bundle.SessionBundleService;
import nl.llm.storyteller.api.http.ApiErrorHandler;
import nl.llm.storyteller.api.http.SessionController;
import nl.llm.storyteller.api.http.StoryController;
import nl.llm.storyteller.api.persistence.Database;
import nl.llm.storyteller.api.persistence.JdbcStoryRepository;
import nl.llm.storyteller.api.persistence.JdbcSessionBundleRepository;
import nl.llm.storyteller.api.persistence.JdbcSessionRepository;
import nl.llm.storyteller.api.persistence.SchemaInitializer;
import nl.llm.storyteller.api.session.SessionCookieService;
import nl.llm.storyteller.api.session.SessionService;
import nl.llm.storyteller.api.story.StoryTurnService;
import nl.llm.storyteller.api.web.WebController;
import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.service.ChatClient;
import nl.llm.storyteller.core.service.OpenAiCompatibleHttpClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ApiServer implements AutoCloseable {
  private final ApiConfig config;
  private final Javalin server;

  private ApiServer(ApiConfig config, Javalin server) {
    this.config = config;
    this.server = server;
  }

  public static ApiServer create(ApiConfig config) {
    AppConfig coreConfig = AppConfig.load();
    return create(
      config,
      coreConfig,
      new OpenAiCompatibleHttpClient(
        coreConfig.openAiCompatibleUrl(), coreConfig.chatModel(), coreConfig.hideReasoningBlocks(),
        coreConfig.openAiCompatibleApiKey()
      ),
      new OpenAiCompatibleHttpClient(
        coreConfig.openAiCompatibleUrl(), coreConfig.validatorModel(), coreConfig.hideReasoningBlocks(),
        coreConfig.openAiCompatibleApiKey()
      )
    );
  }

  static ApiServer create(
    ApiConfig config,
    AppConfig coreConfig,
    ChatClient chatClient,
    ChatClient validationClient
  ) {
    createDatabaseDirectory(config.databasePath());
    Database database = new Database(
      config.databaseUrl(),
      config.databaseUsername(),
      config.databasePassword()
    );
    new SchemaInitializer(database).initialize();

    SessionService sessionService = new SessionService(
      new JdbcSessionRepository(database),
      config.sessionInactivityTimeout()
    );
    sessionService.deleteExpired();
    SessionCookieService cookieService = new SessionCookieService(config.sessionInactivityTimeout());
    SessionController sessionController = new SessionController(sessionService, cookieService);
    JdbcStoryRepository storyRepository = new JdbcStoryRepository(database);
    StoryTurnService storyTurnService = new StoryTurnService(
      storyRepository, coreConfig, chatClient, validationClient
    );
    StoryController storyController = new StoryController(
      sessionService,
      storyTurnService
    );
    SessionBundleService bundleService = new SessionBundleService(
      new JdbcSessionBundleRepository(database), config.sessionInactivityTimeout()
    );
    WebController webController = new WebController(
      sessionService, cookieService, storyRepository, storyTurnService, bundleService
    );
    Javalin server = Javalin.create(javalinConfig -> {
      javalinConfig.startup.showJavalinBanner = false;
      javalinConfig.fileRenderer(new JavalinJte(TemplateEngine.createPrecompiled(ContentType.Html)));
      javalinConfig.staticFiles.add("/public", Location.CLASSPATH);
      sessionController.register(javalinConfig);
      storyController.register(javalinConfig);
      webController.register(javalinConfig);
      ApiErrorHandler.register(javalinConfig);
    });
    return new ApiServer(config, server);
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

  public ApiServer start() {
    server.start(config.host(), config.port());
    return this;
  }

  public int port() {
    return server.port();
  }

  @Override
  public void close() {
    server.stop();
  }
}
