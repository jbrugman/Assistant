package nl.llm.storyteller.api;

import com.fasterxml.jackson.databind.JsonNode;
import nl.llm.storyteller.core.JsonSupport;
import nl.llm.storyteller.core.config.AppConfigLoader;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.service.ChatClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServerTest {
  @TempDir
  Path temporaryDirectory;

  private ApiServer server;

  @AfterEach
  void stopApplication() {
    if (server != null) {
      server.close();
    }
  }

  @Test
  @DisplayName("""
    Given a running Storyteller API,
    When a client creates a session and retrieves it through the returned cookie,
    Then it should return the same active session and renew the cookie
    """)
  void shouldCreateAndRetrieveCurrentSession() throws Exception {
    server = ApiServer.create(config()).start();
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest createRequest = HttpRequest.newBuilder(uri("/v1/sessions"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"  My story  \"}"))
      .build();

    HttpResponse<String> created = client.send(createRequest, HttpResponse.BodyHandlers.ofString());
    String cookie = created.headers().firstValue("Set-Cookie").orElseThrow();
    String cookiePair = cookie.substring(0, cookie.indexOf(';'));
    HttpRequest currentRequest = HttpRequest.newBuilder(uri("/v1/session"))
      .header("Cookie", cookiePair)
      .GET()
      .build();
    HttpResponse<String> current = client.send(currentRequest, HttpResponse.BodyHandlers.ofString());

    JsonNode createdBody = JsonSupport.OBJECT_MAPPER.readTree(created.body());
    JsonNode currentBody = JsonSupport.OBJECT_MAPPER.readTree(current.body());
    assertEquals(201, created.statusCode());
    assertEquals(200, current.statusCode());
    assertEquals("My story", createdBody.path("title").asText());
    assertEquals(createdBody.path("sessionId").asText(), currentBody.path("sessionId").asText());
    assertTrue(current.headers().firstValue("Set-Cookie").orElseThrow().contains("HttpOnly"));
  }

  @Test
  @DisplayName("""
    Given a request without an active-session cookie,
    When the current session endpoint is called,
    Then it should return a structured not-found response
    """)
  void shouldReturnNotFoundWithoutSessionCookie() throws Exception {
    server = ApiServer.create(config()).start();
    HttpRequest request = HttpRequest.newBuilder(uri("/v1/session")).GET().build();

    HttpResponse<String> response = HttpClient.newHttpClient()
      .send(request, HttpResponse.BodyHandlers.ofString());

    JsonNode body = JsonSupport.OBJECT_MAPPER.readTree(response.body());
    assertEquals(404, response.statusCode());
    assertEquals("session_not_found", body.path("code").asText());
  }

  @Test
  @DisplayName("""
    Given an active API session and a responding model backend,
    When two story turns are submitted,
    Then it should return and persist each response while supplying the first turn as context for the second
    """)
  void shouldSubmitStoryTurnsWithSessionHistory() throws Exception {
    Path coreOverride = temporaryDirectory.resolve("core.config");
    Files.writeString(coreOverride, "validation.enabled=false\n");
    RecordingChatClient chatClient = new RecordingChatClient(List.of("First response", "Second response"));
    server = ApiServer.create(
      config(),
      AppConfigLoader.load(temporaryDirectory, coreOverride),
      chatClient,
      chatClient
    ).start();
    HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> created = client.send(
      HttpRequest.newBuilder(uri("/v1/sessions"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{}"))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
    String sessionId = JsonSupport.OBJECT_MAPPER.readTree(created.body()).path("sessionId").asText();

    HttpResponse<String> first = submitTurn(client, sessionId, "First prompt");
    HttpResponse<String> second = submitTurn(client, sessionId, "Second prompt");

    JsonNode firstBody = JsonSupport.OBJECT_MAPPER.readTree(first.body());
    JsonNode secondBody = JsonSupport.OBJECT_MAPPER.readTree(second.body());
    assertEquals(200, first.statusCode());
    assertEquals("First response", firstBody.path("response").asText());
    assertEquals(0, firstBody.path("userMessageIndex").asInt());
    assertEquals(1, firstBody.path("assistantMessageIndex").asInt());
    assertEquals("Second response", secondBody.path("response").asText());
    assertEquals(2, secondBody.path("userMessageIndex").asInt());
    assertEquals(3, secondBody.path("assistantMessageIndex").asInt());
    assertEquals(List.of("system", "user", "assistant", "user"),
      chatClient.requests().get(1).stream().map(Message::role).toList());
  }

  @Test
  @DisplayName("""
    Given the server-rendered web interface,
    When a visitor creates a session and submits a prompt,
    Then it should render the prompt and response in the responsive story page
    """)
  void shouldRenderAndUseStoryPage() throws Exception {
    Path coreOverride = temporaryDirectory.resolve("web-core.config");
    Files.writeString(coreOverride, "validation.enabled=false\n");
    RecordingChatClient chatClient = new RecordingChatClient(List.of("A door opens in the old library."));
    server = ApiServer.create(
      config(),
      AppConfigLoader.load(temporaryDirectory, coreOverride),
      chatClient,
      chatClient
    ).start();
    HttpClient client = HttpClient.newHttpClient();

    HttpResponse<String> start = client.send(
      HttpRequest.newBuilder(uri("/")).GET().build(),
      HttpResponse.BodyHandlers.ofString()
    );
    HttpResponse<String> created = client.send(
      HttpRequest.newBuilder(uri("/web/sessions"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString("title=The+Library"))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
    String cookie = created.headers().firstValue("Set-Cookie").orElseThrow();
    String cookiePair = cookie.substring(0, cookie.indexOf(';'));
    HttpResponse<String> submitted = client.send(
      HttpRequest.newBuilder(uri("/story/turns"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("Cookie", cookiePair)
        .POST(HttpRequest.BodyPublishers.ofString("prompt=Open+the+door"))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
    HttpResponse<String> story = client.send(
      HttpRequest.newBuilder(uri("/story")).header("Cookie", cookiePair).GET().build(),
      HttpResponse.BodyHandlers.ofString()
    );

    assertEquals(200, start.statusCode());
    assertTrue(start.body().contains("Start a story"));
    assertEquals(303, created.statusCode());
    assertEquals(303, submitted.statusCode());
    assertEquals(200, story.statusCode());
    assertTrue(story.body().contains("The Library"));
    assertTrue(story.body().contains("Open the door"));
    assertTrue(story.body().contains("A door opens in the old library."));
    assertTrue(story.body().contains("class=\"exchange\""));
    assertTrue(story.body().contains("conversation.scrollTop = latestTop"));
    assertTrue(story.body().contains("response-width-toggle"));
    assertTrue(story.body().contains("response-layout-toggle"));
    assertTrue(story.body().contains("response-maximized"));
    assertTrue(story.body().contains("single-column"));
    assertTrue(story.body().contains("event.shiftKey"));
    assertTrue(story.body().contains("continueButton.disabled = true"));
    assertTrue(story.body().contains("undoButton.disabled = true"));
    assertTrue(story.body().contains("formaction=\"/story/undo\""));
    assertTrue(story.body().contains("Stop story"));
    assertTrue(story.body().contains("permanently deleted"));
    assertTrue(story.body().contains("Infinite"));

    HttpResponse<String> undone = client.send(
      HttpRequest.newBuilder(uri("/story/undo"))
        .header("Cookie", cookiePair)
        .POST(HttpRequest.BodyPublishers.noBody())
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
    HttpResponse<String> undoneStory = client.send(
      HttpRequest.newBuilder(uri("/story")).header("Cookie", cookiePair).GET().build(),
      HttpResponse.BodyHandlers.ofString()
    );

    assertEquals(303, undone.statusCode());
    assertFalse(undoneStory.body().contains("A door opens in the old library."));
    assertTrue(undoneStory.body().contains("data-undo-available=\"false\""));

    HttpResponse<String> infinite = client.send(
      HttpRequest.newBuilder(uri("/story/infinite"))
        .header("Cookie", cookiePair)
        .POST(HttpRequest.BodyPublishers.noBody())
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
    HttpResponse<String> infiniteStory = client.send(
      HttpRequest.newBuilder(uri("/story")).header("Cookie", cookiePair).GET().build(),
      HttpResponse.BodyHandlers.ofString()
    );

    assertEquals(303, infinite.statusCode());
    assertTrue(infinite.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=2147483647"));
    assertTrue(infiniteStory.body().contains("Use timeout"));

    HttpResponse<String> stopped = client.send(
      HttpRequest.newBuilder(uri("/story/stop"))
        .header("Cookie", cookiePair)
        .POST(HttpRequest.BodyPublishers.noBody())
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
    HttpResponse<String> deletedStory = client.send(
      HttpRequest.newBuilder(uri("/story")).header("Cookie", cookiePair).GET().build(),
      HttpResponse.BodyHandlers.ofString()
    );

    assertEquals(303, stopped.statusCode());
    assertTrue(stopped.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
    assertEquals(303, deletedStory.statusCode());
  }

  private HttpResponse<String> submitTurn(HttpClient client, String sessionId, String prompt) throws Exception {
    return client.send(
      HttpRequest.newBuilder(uri("/v1/sessions/" + sessionId + "/turns"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"prompt\":\"" + prompt + "\"}"))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
  }

  private ApiConfig config() {
    return new ApiConfig(
      "127.0.0.1",
      0,
      temporaryDirectory.resolve("database/api"),
      "sa",
      "",
      Duration.ofMinutes(60)
    );
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + server.port() + path);
  }

  private static final class RecordingChatClient implements ChatClient {
    private final List<String> responses;
    private final List<List<Message>> requests = new ArrayList<>();

    private RecordingChatClient(List<String> responses) {
      this.responses = responses;
    }

    @Override
    public String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds) {
      requests.add(List.copyOf(messages));
      return responses.get(requests.size() - 1);
    }

    private List<List<Message>> requests() {
      return List.copyOf(requests);
    }
  }
}
