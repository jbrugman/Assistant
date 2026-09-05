package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.config.MlxServerConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ManagedMlxServer implements AutoCloseable {
  private static final Duration HEALTH_REQUEST_TIMEOUT = Duration.ofSeconds(1);
  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
  private static final int MAX_CAPTURED_OUTPUT_LENGTH = 4_000;

  private final Process process;
  private final int port;
  private final StringBuilder capturedOutput = new StringBuilder();
  private final Thread outputDrainer;

  private ManagedMlxServer(Process process, int port) {
    this.process = process;
    this.port = port;
    this.outputDrainer = Thread.ofVirtual().name("mlx-server-output").start(this::drainOutput);
  }

  public static ManagedMlxServer start(nl.llm.storyteller.core.config.MlxServerConfig config) throws IOException, InterruptedException {
    if (!Files.exists(config.modelPath())) {
      throw new IOException("MLX model path does not exist: " + config.modelPath());
    }

    int port = config.port() == 0 ? findAvailablePort() : config.port();
    Process process = new ProcessBuilder(buildCommand(config, port))
      .redirectErrorStream(true)
      .start();
    ManagedMlxServer server = new ManagedMlxServer(process, port);
    try {
      server.waitUntilReady(config.startupTimeoutSeconds());
      return server;
    } catch (IOException | InterruptedException ex) {
      server.close();
      throw ex;
    }
  }

  static List<String> buildCommand(nl.llm.storyteller.core.config.MlxServerConfig config, int port) {
    List<String> command = new ArrayList<>();
    command.add(config.command());
    if (!config.arguments().isBlank()) {
      command.addAll(List.of(config.arguments().trim().split("\\s+")));
    }
    command.add("--model");
    command.add(config.modelPath().toString());
    command.add("--host");
    command.add("127.0.0.1");
    command.add("--port");
    command.add(String.valueOf(port));
    return command;
  }

  public String chatCompletionsUrl() {
    return "http://127.0.0.1:" + port + "/v1/chat/completions";
  }

  public long processId() {
    return process.pid();
  }

  private static int findAvailablePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private void waitUntilReady(int startupTimeoutSeconds) throws IOException, InterruptedException {
    HttpClient healthClient = HttpClient.newHttpClient();
    HttpRequest healthRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
      .timeout(HEALTH_REQUEST_TIMEOUT)
      .GET()
      .build();
    Instant deadline = Instant.now().plusSeconds(startupTimeoutSeconds);

    while (Instant.now().isBefore(deadline)) {
      if (!process.isAlive()) {
        throw new IOException("MLX server exited before becoming ready: " + capturedOutput());
      }
      try {
        HttpResponse<Void> response = healthClient.send(healthRequest, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 200) {
          return;
        }
      } catch (IOException _) {
        // The server may not have bound its loopback socket yet.
      }
      Thread.sleep(200);
    }
    throw new IOException("Timed out waiting for MLX server readiness: " + capturedOutput());
  }

  private void drainOutput() {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        appendOutput(line);
      }
    } catch (IOException _) {
      // The process closing its output stream is expected during shutdown.
    }
  }

  private synchronized void appendOutput(String line) {
    capturedOutput.append(line).append(System.lineSeparator());
    if (capturedOutput.length() > MAX_CAPTURED_OUTPUT_LENGTH) {
      capturedOutput.delete(0, capturedOutput.length() - MAX_CAPTURED_OUTPUT_LENGTH);
    }
  }

  private synchronized String capturedOutput() {
    return capturedOutput.toString().trim();
  }

  @Override
  public void close() {
    process.destroy();
    try {
      if (!process.waitFor(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
      }
    } catch (InterruptedException _) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
    } finally {
      outputDrainer.interrupt();
    }
  }
}
