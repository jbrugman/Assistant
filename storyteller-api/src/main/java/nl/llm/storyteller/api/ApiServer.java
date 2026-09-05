package nl.llm.storyteller.api;

import io.javalin.Javalin;
import nl.llm.storyteller.api.http.ApiErrorHandler;
import nl.llm.storyteller.api.http.SessionController;
import nl.llm.storyteller.api.persistence.Database;
import nl.llm.storyteller.api.persistence.JdbcSessionRepository;
import nl.llm.storyteller.api.persistence.SchemaInitializer;
import nl.llm.storyteller.api.session.SessionCookieService;
import nl.llm.storyteller.api.session.SessionService;

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
    SessionController sessionController = new SessionController(
      sessionService,
      new SessionCookieService(config.sessionInactivityTimeout())
    );
    Javalin server = Javalin.create(javalinConfig -> {
      javalinConfig.startup.showJavalinBanner = false;
      sessionController.register(javalinConfig);
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
