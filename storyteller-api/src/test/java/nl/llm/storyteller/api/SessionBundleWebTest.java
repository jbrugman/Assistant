package nl.llm.storyteller.api;

import nl.llm.storyteller.core.JsonSupport;
import nl.llm.storyteller.core.config.AppConfigLoader;
import nl.llm.storyteller.core.service.ChatClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionBundleWebTest {
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
    Given a CLI session ZIP,
    When it is imported through the web interface and exported again,
    Then a new active session should contain the complete portable bundle
    """)
  void shouldImportAndExportSessionBundle() throws Exception {
    Path coreOverride = temporaryDirectory.resolve("core.config");
    Files.writeString(coreOverride, "validation.enabled=false\n");
    ChatClient unusedClient = (_, _, _) -> "unused";
    server = ApiServer.create(
      config(),
      AppConfigLoader.load(temporaryDirectory, coreOverride),
      unusedClient,
      unusedClient
    ).start();
    HttpClient client = HttpClient.newHttpClient();
    byte[] archive = archive();
    String boundary = "storyteller-test-boundary";

    HttpResponse<String> imported = client.send(
      HttpRequest.newBuilder(uri("/import"))
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, archive)))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
    String cookie = imported.headers().firstValue("Set-Cookie").orElseThrow();
    String cookiePair = cookie.substring(0, cookie.indexOf(';'));
    HttpResponse<String> story = client.send(
      HttpRequest.newBuilder(uri("/story")).header("Cookie", cookiePair).GET().build(),
      HttpResponse.BodyHandlers.ofString()
    );
    HttpResponse<byte[]> exported = client.send(
      HttpRequest.newBuilder(uri("/export")).header("Cookie", cookiePair).GET().build(),
      HttpResponse.BodyHandlers.ofByteArray()
    );
    Map<String, String> files = unzip(exported.body());

    assertEquals(303, imported.statusCode());
    assertEquals(200, story.statusCode());
    assertTrue(story.body().contains("Open the archive"));
    assertTrue(story.body().contains("The archive opens."));
    assertEquals(200, exported.statusCode());
    assertEquals("application/zip", exported.headers().firstValue("Content-Type").orElseThrow());
    assertTrue(exported.headers().firstValue("Content-Disposition").orElseThrow().contains("attachment"));
    assertEquals("Long-term summary", files.get("summary.md"));
    assertEquals("Recent summary", files.get("recent-summary.md"));
    assertEquals("location: library", files.get("canonical-state.yaml"));
    assertTrue(files.get("turn-state.json").contains("turns_this_round"));
    assertTrue(files.get("history.json").contains("Open the archive"));
    assertTrue(files.get("knowledge-graph.json").contains("schemaVersion"));
    assertTrue(files.get("manifest.json").contains("storyteller-session"));
  }

  private byte[] archive() throws Exception {
    Map<String, String> files = new HashMap<>();
    files.put("history.json", """
      {
        "messages": [
          {"role":"user","content":"Open the archive"},
          {"role":"assistant","content":"The archive opens."}
        ],
        "summary_cursor": 2,
        "recent_summary_cursor": 2,
        "canonical_state_cursor": 2
      }
      """);
    files.put("summary.md", "Long-term summary");
    files.put("recent-summary.md", "Recent summary");
    files.put("canonical-state.yaml", "location: library");
    files.put("knowledge-graph.json", JsonSupport.OBJECT_MAPPER.writeValueAsString(Map.of(
      "schemaVersion", 1,
      "revision", 0,
      "entities", Map.of(),
      "facts", java.util.List.of()
    )));
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
      for (Map.Entry<String, String> file : files.entrySet()) {
        zip.putNextEntry(new ZipEntry(file.getKey()));
        zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return output.toByteArray();
  }

  private byte[] multipart(String boundary, byte[] archive) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    output.write(("Content-Disposition: form-data; name=\"bundle\"; filename=\"cli-session.zip\"\r\n")
      .getBytes(StandardCharsets.UTF_8));
    output.write("Content-Type: application/zip\r\n\r\n".getBytes(StandardCharsets.UTF_8));
    output.write(archive);
    output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return output.toByteArray();
  }

  private Map<String, String> unzip(byte[] archive) throws Exception {
    Map<String, String> files = new HashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return files;
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
