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

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtendedApiTest {
  private static final String GRAPH = """
    {"schemaVersion":1,"revision":0,"entities":{
      "character.alice":{"type":"CHARACTER","name":"Alice","aliases":[],"source":"MANUAL"},
      "item.compass":{"type":"ITEM","name":"Compass","aliases":[],"source":"TURNBASED"}
    },"facts":[]}
    """;
  private static final byte[] PNG = Base64.getDecoder().decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jVj8AAAAASUVORK5CYII="
  );

  @TempDir
  Path temporaryDirectory;
  private final HttpClient client = HttpClient.newHttpClient();
  private final AtomicReference<List<Message>> modelMessages = new AtomicReference<>();
  private ApiServer server;
  private ApiConfig config;
  private String sessionId;
  private String cookie;
  private boolean failGraphModel;

  @AfterEach
  void closeServer() {
    if (server != null) server.close();
  }

  @Test
  @DisplayName("Stopping the active session deletes finite data, preserves infinite data and always clears the cookie")
  void shouldStopActiveSession() throws Exception {
    startSession();
    assertEquals(200, request("POST", "/turns", "{\"prompt\":\"Hello\"}").statusCode());
    assertEquals(200, request("PATCH", "", "{\"infinite\":true}").statusCode());
    HttpResponse<byte[]> stopped = send("POST", "/v1/session/stop", "application/json", new byte[0]);
    assertEquals(204, stopped.statusCode());
    assertEquals(0, stopped.body().length);
    assertTrue(stopped.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
    cookie = null;
    assertEquals(404, send("GET", "/v1/session", "application/json", new byte[0]).statusCode());
    assertEquals(2, json(request("GET", "/history", null)).path("messages").size());
    HttpResponse<byte[]> resumed = request("POST", "/resume", "");
    assertEquals(200, resumed.statusCode());
    cookie = resumed.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    assertEquals(200, request("PATCH", "", "{\"infinite\":false}").statusCode());
    stopped = send("POST", "/v1/session/stop", "application/json", new byte[0]);
    assertEquals(204, stopped.statusCode());
    assertTrue(stopped.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
    assertEquals(404, request("GET", "", null).statusCode());
    assertEquals(404, request("GET", "/history", null).statusCode());
    // Stale and absent cookies are harmless, matching the web stop operation.
    assertEquals(204, send("POST", "/v1/session/stop", "application/json", new byte[0]).statusCode());
    cookie = null;
    assertEquals(204, send("POST", "/v1/session/stop", "application/json", new byte[0]).statusCode());
  }

  @Test
  @DisplayName("Given a session, when lifetime is set and resumed, then infinite sessions are protected and finite sessions can be deleted")
  void shouldManageLifetimeResumeAndDeletion() throws Exception {
    startSession();
    assertEquals(404, request("POST", "/resume", "").statusCode());
    for (String body : List.of("{}", "null", "[]", "{\"infinite\":null}", "{\"infinite\":\"true\"}", "{\"infinite\":1}", "{\"infinite\":true,\"title\":\"No\"}")) {
      assertEquals(400, request("PATCH", "", body).statusCode(), body);
    }
    for (int repeat = 0; repeat < 2; repeat++) {
      HttpResponse<byte[]> changed = request("PATCH", "", "{\"infinite\":true}");
      assertEquals(200, changed.statusCode());
      assertTrue(json(changed).path("infinite").asBoolean());
      assertTrue(changed.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=2147483647"));
    }
    HttpResponse<byte[]> resumed = request("POST", "/resume", "");
    assertEquals(200, resumed.statusCode());
    assertEquals(sessionId, json(resumed).path("sessionId").asText());
    assertTrue(resumed.headers().firstValue("Set-Cookie").orElseThrow().contains(sessionId));
    HttpResponse<byte[]> protectedSession = request("DELETE", "", "");
    assertEquals(409, protectedSession.statusCode());
    assertEquals("session_infinite", json(protectedSession).path("code").asText());
    assertFalse(json(request("PATCH", "", "{\"infinite\":false}")).path("infinite").asBoolean());
    HttpResponse<byte[]> deleted = request("DELETE", "", "");
    assertEquals(204, deleted.statusCode());
    assertTrue(deleted.headers().firstValue("Set-Cookie").orElseThrow().contains("Max-Age=0"));
    assertEquals(404, request("GET", "", null).statusCode());
  }

  @Test
  @DisplayName("Given a session with an image and edited prompts, when exported and imported, then history, image bytes and prompts survive")
  void shouldUploadRetrieveAndRoundTripSessionBundle() throws Exception {
    startSession();
    assertEquals(200, multipart(sessionPath("/turns"), "image", "image.png", "image/png", PNG, "Hello", null).statusCode());
    assertTrue(modelMessages.get().getLast().imageDataUrl().startsWith("data:image/png;base64,"));
    HttpResponse<byte[]> image = request("GET", "/images/0", null);
    assertEquals(200, image.statusCode());
    assertArrayEquals(PNG, image.body());
    assertEquals("image/png", image.headers().firstValue("Content-Type").orElseThrow());
    assertEquals("private, no-store", image.headers().firstValue("Cache-Control").orElseThrow());
    assertEquals(404, request("GET", "/images/1", null).statusCode());
    assertEquals(400, request("GET", "/images/-1", null).statusCode());
    assertEquals(200, request("PATCH", "/config", "{\"systemPrompt\":\"Stored override\"}").statusCode());
    JsonNode history = json(request("GET", "/history", null)).path("messages");
    HttpResponse<byte[]> exported = request("GET", "/export", null);
    assertEquals(200, exported.statusCode());
    assertEquals("application/zip", exported.headers().firstValue("Content-Type").orElseThrow());
    assertTrue(exported.headers().firstValue("Content-Disposition").orElseThrow().contains(".zip"));
    String originalId = sessionId;
    HttpResponse<byte[]> imported = multipart("/v1/session-imports", "bundle", "session.zip", "application/zip", exported.body(), null, null);
    assertEquals(201, imported.statusCode(), new String(imported.body(), StandardCharsets.UTF_8));
    sessionId = json(imported).path("sessionId").asText();
    assertFalse(sessionId.equals(originalId));
    assertTrue(imported.headers().firstValue("Set-Cookie").orElseThrow().contains(sessionId));
    assertEquals(history, json(request("GET", "/history", null)).path("messages"));
    assertEquals("Stored override", json(request("GET", "/config", null)).path("systemPrompt").asText());
    assertArrayEquals(PNG, request("GET", "/images/0", null).body());
    assertEquals(400, multipart("/v1/session-imports", "wrong", "bad.zip", "application/zip", new byte[]{1}, null, null).statusCode());
    assertEquals(400, multipart("/v1/session-imports", "bundle", "bad.zip", "application/zip", new byte[]{1}, null, null).statusCode());
  }

  @Test
  @DisplayName("Given old exchanges, when referenced by JSON or multipart turns, then the model receives historical context and invalid references are rejected")
  void shouldSupportPastContextInBothTurnFormats() throws Exception {
    startSession();
    assertEquals(200, request("POST", "/turns", "{\"prompt\":\"First\"}").statusCode());
    assertEquals(200, request("POST", "/turns", "{\"prompt\":\"Second\"}").statusCode());
    assertEquals(200, request("POST", "/turns", "{\"prompt\":\"Third\",\"pastMessageIndexes\":[0]}").statusCode());
    assertTrue(modelMessages.get().stream().anyMatch(message -> message.content().contains("RELEVANT PAST STORY EXCERPTS")));
    assertTrue(modelMessages.get().stream().anyMatch(message -> message.content().contains("First")));
    assertEquals(200, multipart(sessionPath("/turns"), "image", "image.png", "image/png", PNG, "Fourth", "0").statusCode());
    assertTrue(modelMessages.get().getLast().imageDataUrl().startsWith("data:image/png;base64,"));
    assertTrue(modelMessages.get().stream().anyMatch(message -> message.content().contains("RELEVANT PAST STORY EXCERPTS")));
    for (String indexes : List.of("[-1]", "[99]", "[0,2,4,6]", "[null]", "[0.5]")) {
      assertEquals(400, request("POST", "/turns", "{\"prompt\":\"Invalid\",\"pastMessageIndexes\":" + indexes + "}").statusCode());
    }
    assertEquals(400, multipart(sessionPath("/turns"), "image", "image.png", "image/png", PNG, "Invalid", "abc").statusCode());
    assertEquals(8, json(request("GET", "/history", null)).path("messages").size());
  }

  @Test
  @DisplayName("Given invalid image uploads, when submitted, then media, dimensions and size limits are enforced before persistence")
  void shouldRejectInvalidImages() throws Exception {
    startSession();
    byte[] oversizedDimensions = PNG.clone();
    ByteBuffer.wrap(oversizedDimensions).putInt(16, 1025);
    for (byte[] image : List.of(new byte[]{1, 2, 3}, oversizedDimensions)) {
      assertEquals(400, multipart(sessionPath("/turns"), "image", "bad.png", "image/png", image, "Invalid", null).statusCode());
    }
    byte[] gif = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    assertEquals(400, multipart(sessionPath("/turns"), "image", "image.gif", "image/gif", gif, "Invalid", null).statusCode());
    byte[] oversized = new byte[nl.llm.storyteller.db.StoryImage.MAX_UPLOAD_BYTES + 1];
    System.arraycopy(PNG, 0, oversized, 0, PNG.length);
    HttpResponse<byte[]> tooLarge = multipart(sessionPath("/turns"), "image", "big.png", "image/png", oversized, "Invalid", null);
    assertEquals(413, tooLarge.statusCode());
    assertEquals("payload_too_large", json(tooLarge).path("code").asText());
    assertEquals(0, json(request("GET", "/history", null)).path("messages").size());
  }

  @Test
  @DisplayName("Given a graph, when replaced, reset, filled or emptied, then validated persisted graph revisions are returned")
  void shouldManageGraphAndRejectStaleReplacements() throws Exception {
    startSession();
    JsonNode replaced = json(request("PUT", "/graph", GRAPH)).path("knowledgeGraph");
    assertEquals(1, replaced.path("revision").asLong());
    assertEquals(2, replaced.path("entities").size());
    HttpResponse<byte[]> stale = request("PUT", "/graph", GRAPH);
    assertEquals(409, stale.statusCode());
    assertEquals("graph_conflict", json(stale).path("code").asText());
    JsonNode reset = json(request("POST", "/graph/reset-turn-based", "")).path("knowledgeGraph");
    assertEquals(2, reset.path("revision").asLong());
    assertEquals(1, reset.path("entities").size());
    assertTrue(reset.path("entities").has("character.alice"));
    JsonNode filled = json(request("POST", "/graph/fill-fixed-protagonists", "")).path("knowledgeGraph");
    assertEquals(3, filled.path("revision").asLong());
    assertEquals("FIXED_PROTAGONIST", filled.path("entities").path("character.alice").path("source").asText());
    JsonNode empty = json(request("POST", "/graph/generate-empty", "")).path("knowledgeGraph");
    assertEquals(4, empty.path("revision").asLong());
    assertEquals(0, empty.path("entities").size());
    for (String invalid : List.of("", "null", "[]", "{}", "{\"schemaVersion\":99,\"revision\":4,\"entities\":{},\"facts\":[]}")) {
      assertEquals(400, request("PUT", "/graph", invalid).statusCode(), invalid);
    }
    assertEquals(empty, json(request("GET", "/state", null)).path("knowledgeGraph"));
  }

  @Test
  @DisplayName("Given persisted memory, when each layer is reset, then only that layer and its cursor are cleared")
  void shouldResetIndividualMemoryLayers() throws Exception {
    startSession();
    try (var connection = DriverManager.getConnection(config.databaseUrl(), "sa", "");
         var statement = connection.prepareStatement("UPDATE session_memory SET summary_content='Summary', recent_summary_content='Recent', canonical_state_content='Canonical', summary_cursor=4, recent_summary_cursor=4, canonical_state_cursor=4 WHERE session_id=?")) {
      statement.setString(1, sessionId);
      statement.executeUpdate();
    }
    assertEquals(204, request("POST", "/memory/reset/mid-term", "").statusCode());
    JsonNode mid = json(request("GET", "/state", null));
    assertEquals("", mid.path("recentSummary").asText());
    assertEquals(0, mid.path("recentSummaryCursor").asInt());
    assertEquals("Summary", mid.path("summary").asText());
    assertEquals("Canonical", mid.path("canonicalState").asText());
    assertEquals(4, mid.path("summaryCursor").asInt());
    assertEquals(204, request("POST", "/memory/reset/long-term", "").statusCode());
    JsonNode longTerm = json(request("GET", "/state", null));
    assertEquals("", longTerm.path("summary").asText());
    assertEquals(0, longTerm.path("summaryCursor").asInt());
    assertEquals("Canonical", longTerm.path("canonicalState").asText());
    assertEquals(204, request("POST", "/memory/reset/canonical-state", "").statusCode());
    JsonNode canonical = json(request("GET", "/state", null));
    assertEquals("", canonical.path("canonicalState").asText());
    assertEquals(0, canonical.path("canonicalStateCursor").asInt());
  }

  @Test
  @DisplayName("Given an unknown session, when any new session-scoped endpoint is called, then it returns a JSON not-found error")
  void shouldRejectUnknownSessions() throws Exception {
    startSession();
    sessionId = "missing";
    for (String path : List.of("/export", "/images/0")) {
      assertEquals(404, request("GET", path, null).statusCode());
    }
    for (String path : List.of("/graph/reset-turn-based", "/graph/fill-fixed-protagonists", "/graph/generate-empty",
      "/memory/reset/mid-term", "/memory/reset/long-term", "/memory/reset/canonical-state", "/resume")) {
      assertEquals(404, request("POST", path, "").statusCode());
    }
    assertEquals(404, request("PATCH", "", "{\"infinite\":true}").statusCode());
    assertEquals(404, request("DELETE", "", "").statusCode());
    assertEquals(404, request("PUT", "/graph", GRAPH).statusCode());
  }

  @Test
  @DisplayName("Given a failing graph backend, when graph generation is requested, then the graph remains unchanged and the API reports the backend error")
  void shouldReportGraphBackendFailureWithoutChangingState() throws Exception {
    failGraphModel = true;
    startSession();
    JsonNode original = json(request("GET", "/state", null)).path("knowledgeGraph");
    HttpResponse<byte[]> failure = request("POST", "/graph/fill-fixed-protagonists", "");
    assertEquals(502, failure.statusCode());
    assertEquals("model_backend_error", json(failure).path("code").asText());
    assertEquals(original, json(request("GET", "/state", null)).path("knowledgeGraph"));
    assertEquals(400, send("POST", "/v1/session-imports", "application/json", "{}".getBytes(StandardCharsets.UTF_8)).statusCode());
    assertEquals(400, send("POST", "/v1/session-imports", "multipart/form-data; boundary=broken", "invalid multipart".getBytes(StandardCharsets.UTF_8)).statusCode());
  }

  private void startSession() throws Exception {
    Path coreOverride = temporaryDirectory.resolve("core.config");
    Files.writeString(coreOverride, "validation.enabled=false\ngraph.enabled=false\nchat.maxRecentTurns=1\n");
    config = new ApiConfig("127.0.0.1", 0, temporaryDirectory.resolve("database/api"), "sa", "", Duration.ofMinutes(60));
    ChatClient model = (messages, _, _) -> {
      modelMessages.set(List.copyOf(messages));
      return "Story response";
    };
    server = ApiServer.create(config, AppConfigLoader.load(temporaryDirectory, coreOverride), model, model,
      (_, _, _) -> "Memory", (_, _, _) -> {
        if (failGraphModel) throw new java.io.IOException("Graph backend unavailable.");
        return GRAPH;
      });
    server.start();
    HttpResponse<byte[]> created = send("POST", "/v1/sessions", "application/json", "{}".getBytes(StandardCharsets.UTF_8));
    assertEquals(201, created.statusCode());
    sessionId = json(created).path("sessionId").asText();
    cookie = created.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
  }

  private String sessionPath(String suffix) {
    return "/v1/sessions/" + sessionId + suffix;
  }

  private HttpResponse<byte[]> request(String method, String suffix, String body) throws Exception {
    return send(method, sessionPath(suffix), "application/json", body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
  }

  private HttpResponse<byte[]> send(String method, String path, String contentType, byte[] body) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
      .header("Content-Type", contentType).method(method, HttpRequest.BodyPublishers.ofByteArray(body));
    if (cookie != null) builder.header("Cookie", cookie);
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
  }

  private HttpResponse<byte[]> multipart(String path, String field, String filename, String type, byte[] file,
                                       String prompt, String pastExchange) throws Exception {
    String boundary = "story-api-test-boundary";
    var body = new ByteArrayOutputStream();
    if (prompt != null) {
      body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"prompt\"\r\n\r\n" + prompt + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
    if (pastExchange != null) {
      body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"pastExchange\"\r\n\r\n" + pastExchange + "\r\n").getBytes(StandardCharsets.UTF_8));
    }
    body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\nContent-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    body.write(file);
    body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return send("POST", path, "multipart/form-data; boundary=" + boundary, body.toByteArray());
  }

  private JsonNode json(HttpResponse<byte[]> response) throws Exception {
    assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
    return JsonSupport.OBJECT_MAPPER.readTree(response.body());
  }
}
