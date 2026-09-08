package nl.llm.storyteller.api;

import java.util.concurrent.CountDownLatch;

public final class ApiApplication implements AutoCloseable {
  private final ApiServer server;

  private ApiApplication(ApiServer server) {
    this.server = server;
  }

  public static ApiApplication create() {
    return new ApiApplication(ApiServer.create(ApiConfig.load()));
  }

  public void start() {
    server.start();
  }

  @Override
  public void close() {
    server.close();
  }

  static void main() throws InterruptedException {
    CountDownLatch shutdown = new CountDownLatch(1);
    Runtime.getRuntime().addShutdownHook(new Thread(shutdown::countDown, "storyteller-api-shutdown"));
    try (ApiApplication application = ApiApplication.create()) {
      application.start();
      shutdown.await();
    }
  }
}
