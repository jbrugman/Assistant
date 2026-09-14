package nl.llm.storyteller.core.service.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import nl.llm.storyteller.core.JsonSupport;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class OpenAiBackendIdentity implements BackendIdentity {
  private final URI modelsUri;
  private final String apiKey;
  private final HttpClient httpClient;
  private volatile Boolean omlx;

  public OpenAiBackendIdentity(String chatCompletionsUrl, String apiKey) {
    this.modelsUri = modelsUri(chatCompletionsUrl);
    this.apiKey = apiKey;
    this.httpClient = HttpClient.newHttpClient();
  }

  @Override
  public boolean isOmlx() throws IOException, InterruptedException {
    Boolean known = omlx;
    if (known != null) {
      return known;
    }
    HttpRequest.Builder request = HttpRequest.newBuilder(modelsUri)
      .timeout(Duration.ofSeconds(10))
      .GET();
    if (!apiKey.isBlank()) {
      request.header("Authorization", "Bearer " + apiKey);
    }
    HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      return false;
    }
    boolean detected = containsOmlxModel(response.body());
    omlx = detected;
    return detected;
  }

  static boolean containsOmlxModel(String body) throws IOException {
    try {
      JsonNode models = JsonSupport.OBJECT_MAPPER.readTree(body).path("data");
      if (!models.isArray()) {
        return false;
      }
      for (JsonNode model : models) {
        if ("omlx".equalsIgnoreCase(model.path("owned_by").asText())) {
          return true;
        }
      }
      return false;
    } catch (JsonProcessingException ex) {
      throw new IOException("OpenAI-compatible models endpoint returned invalid JSON.", ex);
    }
  }

  static URI modelsUri(String chatCompletionsUrl) {
    URI source = URI.create(chatCompletionsUrl);
    String path = source.getPath();
    String suffix = "/chat/completions";
    String modelsPath = path.endsWith(suffix)
      ? path.substring(0, path.length() - suffix.length()) + "/models"
      : path.replaceFirst("/+$", "") + "/models";
    try {
      return new URI(source.getScheme(), source.getAuthority(), modelsPath, source.getQuery(), source.getFragment());
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException("Could not derive models endpoint from " + chatCompletionsUrl, ex);
    }
  }
}
