package nl.llm.storyteller.api;

public final class ApiApplication implements AutoCloseable {
  private final ApiServer server;

  private ApiApplication(ApiServer server) {
    this.server = server;
  }

  public static ApiApplication create() {
    return new ApiApplication(ApiServer.create(ApiConfig.load()));
  }

  public ApiApplication start() {
    server.start();
    return this;
  }

  @Override
  public void close() {
    server.close();
  }

  public static void main(String[] args) {
    ApiApplication application = ApiApplication.create();
    Runtime.getRuntime().addShutdownHook(new Thread(application::close, "storyteller-api-shutdown"));
    application.start();
  }
}
