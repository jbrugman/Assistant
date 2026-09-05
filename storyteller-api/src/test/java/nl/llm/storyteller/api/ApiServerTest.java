package nl.llm.storyteller.api;

import com.fasterxml.jackson.databind.JsonNode;
import nl.llm.storyteller.core.JsonSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
