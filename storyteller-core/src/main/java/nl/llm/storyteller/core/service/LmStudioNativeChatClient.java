package nl.llm.storyteller.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import nl.llm.storyteller.core.JsonSupport;
import nl.llm.storyteller.core.model.Message;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LmStudioNativeChatClient implements ChatClient {
  private static final List<String> SUPPORTED_OPTIONS = List.of(
    "temperature", "top_p", "top_k", "min_p", "repeat_penalty"
  );

  private final URI chatUri;
  private final URI modelsUri;
  private final String configuredModel;
  private final String apiKey;
  private final ChatRequestMetrics metrics;
  private final String purpose;
  private final HttpClient httpClient;

  public LmStudioNativeChatClient(
    String openAiCompatibleUrl,
    String configuredModel,
    String apiKey,
    ChatRequestMetrics metrics
  ) {
    this(openAiCompatibleUrl, configuredModel, apiKey, metrics, "non-reasoning");
  }

  public LmStudioNativeChatClient(
    String openAiCompatibleUrl,
    String configuredModel,
    String apiKey,
    ChatRequestMetrics metrics,
    String purpose
  ) {
    URI backendUri = URI.create(openAiCompatibleUrl);
    String authority = backendUri.getScheme() + "://" + backendUri.getAuthority();
    this.chatUri = URI.create(authority + "/api/v1/chat");
    this.modelsUri = URI.create(authority + "/api/v1/models");
    this.configuredModel = configuredModel;
    this.apiKey = apiKey;
    this.metrics = metrics;
    this.purpose = purpose;
    this.httpClient = HttpClient.newHttpClient();
  }

  @Override
  public String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds)
    throws IOException, InterruptedException {
    String model = configuredModel.isBlank() ? findLoadedModel(timeoutSeconds) : configuredModel;
    Map<String, Object> payload = payload(messages, options, model);
    HttpRequest request = request(chatUri, "POST", payload, timeoutSeconds);
    long started = System.nanoTime();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    requireSuccess(response);
    JsonNode body = JsonSupport.OBJECT_MAPPER.readTree(response.body());
    JsonNode message = findMessage(body.path("output"));
    metrics.recordRequest(
      purpose,
      body.path("stats").path("total_output_tokens").asLong(-1),
      Duration.ofNanos(System.nanoTime() - started)
    );
    return message.path("content").asText();
  }

  Map<String, Object> payload(List<Message> messages, Map<String, Object> options, String model) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", model);
    payload.put("system_prompt", messages.stream()
      .filter(message -> "system".equals(message.role()))
      .map(Message::content)
      .findFirst()
      .orElse(""));
    payload.put("input", messages.stream()
      .filter(message -> !"system".equals(message.role()))
      .map(Message::content)
      .reduce((left, right) -> left + "\n\n" + right)
      .orElse(""));
    SUPPORTED_OPTIONS.forEach(option -> copyOption(options, payload, option));
    if (options.containsKey("max_tokens")) {
      payload.put("max_output_tokens", options.get("max_tokens"));
    }
    payload.put("reasoning", "off");
    payload.put("store", false);
    return payload;
  }

  private String findLoadedModel(int timeoutSeconds) throws IOException, InterruptedException {
    HttpResponse<String> response = httpClient.send(
      request(modelsUri, "GET", null, timeoutSeconds),
      HttpResponse.BodyHandlers.ofString()
    );
    requireSuccess(response);
    JsonNode models = JsonSupport.OBJECT_MAPPER.readTree(response.body()).path("models");
    List<String> loadedModels = java.util.stream.StreamSupport.stream(models.spliterator(), false)
      .filter(model -> !model.path("loaded_instances").isEmpty())
      .map(model -> model.path("key").asText())
      .filter(model -> !model.isBlank())
      .toList();
    if (loadedModels.size() != 1) {
      throw new IllegalStateException(
        "LM Studio non-reasoning requests require exactly one loaded model when no model is configured; found "
          + loadedModels.size() + "."
      );
    }
    return loadedModels.getFirst();
  }

  private HttpRequest request(URI uri, String method, Map<String, Object> payload, int timeoutSeconds)
    throws IOException {
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
      .header("Content-Type", "application/json")
      .timeout(Duration.ofSeconds(timeoutSeconds));
    if (!apiKey.isBlank()) {
      builder.header("Authorization", "Bearer " + apiKey);
    }
    return payload == null
      ? builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
      : builder.method(method, HttpRequest.BodyPublishers.ofString(
        JsonSupport.OBJECT_MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8
      )).build();
  }

  private void requireSuccess(HttpResponse<String> response) throws IOException {
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("LM Studio native API returned HTTP " + response.statusCode() + ": " + response.body());
    }
  }

  private JsonNode findMessage(JsonNode output) {
    for (JsonNode item : output) {
      if ("message".equals(item.path("type").asText()) && item.path("content").isTextual()) {
        return item;
      }
    }
    throw new IllegalArgumentException("LM Studio native API returned no message output.");
  }

  private void copyOption(Map<String, Object> source, Map<String, Object> target, String option) {
    if (source.containsKey(option)) {
      target.put(option, source.get(option));
    }
  }
}
