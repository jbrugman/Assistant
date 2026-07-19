package nl.jbrugman.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

final class LMStudioClient {
    private static final Pattern REASONING_PATTERN = Pattern.compile(
        "<(?:think|thinking|reasoning)>.*?</(?:think|thinking|reasoning)>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final String url;
    private final String model;
    private final boolean hideReasoningBlocks;
    private final HttpClient httpClient;

    LMStudioClient(String url, String model, boolean hideReasoningBlocks) {
        this.url = Objects.requireNonNull(url);
        this.model = Objects.requireNonNull(model);
        this.hideReasoningBlocks = hideReasoningBlocks;
        this.httpClient = HttpClient.newBuilder().build();
    }

    String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds)
        throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages.stream().map(Message::toMap).toList());
        payload.putAll(options);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .POST(HttpRequest.BodyPublishers.ofString(
                JsonSupport.OBJECT_MAPPER.writeValueAsString(payload),
                StandardCharsets.UTF_8
            ))
            .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int statusCode = response.statusCode();
        String responseBody;
        try (InputStream body = response.body()) {
            responseBody = new String(body.readAllBytes(), StandardCharsets.UTF_8);
        }

        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("HTTP " + statusCode + " van LM Studio: " + responseBody);
        }

        JsonNode data;
        try {
            data = JsonSupport.OBJECT_MAPPER.readTree(responseBody);
        } catch (JsonProcessingException ex) {
            String snippet = responseBody.substring(0, Math.min(responseBody.length(), 500)).replace("\n", "\\n");
            throw new IllegalArgumentException(
                "LM Studio gaf geen geldige JSON terug: " + ex.getOriginalMessage() + ". Response begon met: " + snippet,
                ex
            );
        }

        JsonNode contentNode = data.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode()) {
            throw new IllegalArgumentException("LM Studio response bevat geen choices[0].message.content.");
        }

        return stripReasoningBlocks(contentNode.asText());
    }

    private String stripReasoningBlocks(String content) {
        if (!hideReasoningBlocks || content == null || content.isBlank()) {
            return content;
        }

        return REASONING_PATTERN.matcher(content).replaceAll("").trim();
    }
}
