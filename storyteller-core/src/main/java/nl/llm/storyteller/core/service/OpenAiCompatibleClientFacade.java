package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.service.openai.OpenAiRoute;
import nl.llm.storyteller.core.service.openai.OpenAiRouteResult;
import nl.llm.storyteller.core.service.openai.UnsupportedResponsesEndpointException;
import nl.llm.storyteller.core.service.openai.chatcompletions.ChatCompletionsRoute;
import nl.llm.storyteller.core.service.openai.responses.ResponsesRoute;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class OpenAiCompatibleClientFacade implements ChatClient {
  private static final ResponsesCapabilityCache RESPONSES_CAPABILITIES = new ResponsesCapabilityCache();
  private static final Pattern REASONING_PATTERN = Pattern.compile(
    "<[^>]*(?:think|thinking|reasoning|channel)[^>]*>.*?"
      + "(?:<[^>]*(?:think|thinking|reasoning|channel)[^>]*>|\\z)",
    Pattern.CASE_INSENSITIVE | Pattern.DOTALL
  );

  private final boolean hideReasoningBlocks;
  private final ChatRequestMetrics metrics;
  private final String metricsPurpose;
  private final OpenAiRoute responses;
  private final OpenAiRoute chatCompletions;
  private final boolean preferResponses;
  private final AtomicReference<ResponsesCapabilityCache.Support> responsesSupport;

  public OpenAiCompatibleClientFacade(
    String url, String model, boolean hideReasoningBlocks, String apiKey,
    ChatRequestMetrics metrics, String metricsPurpose, boolean googleBackend
  ) {
    this(url, model, hideReasoningBlocks, apiKey, metrics, metricsPurpose, googleBackend, false);
  }

  public OpenAiCompatibleClientFacade(
    String url, String model, boolean hideReasoningBlocks, String apiKey,
    ChatRequestMetrics metrics, String metricsPurpose, boolean googleBackend, boolean disableReasoning
  ) {
    this(
      hideReasoningBlocks, metrics, metricsPurpose,
      new ResponsesRoute(url, model, apiKey, disableReasoning),
      new ChatCompletionsRoute(url, model, apiKey, googleBackend, disableReasoning),
      RESPONSES_CAPABILITIES.forEndpoint(url),
      !model.isBlank()
    );
  }

  OpenAiCompatibleClientFacade(
    boolean hideReasoningBlocks, ChatRequestMetrics metrics, String metricsPurpose,
    OpenAiRoute responses, OpenAiRoute chatCompletions
  ) {
    this(hideReasoningBlocks, metrics, metricsPurpose, responses, chatCompletions,
      new AtomicReference<>(ResponsesCapabilityCache.Support.UNKNOWN), true);
  }

  OpenAiCompatibleClientFacade(
    boolean hideReasoningBlocks, ChatRequestMetrics metrics, String metricsPurpose,
    OpenAiRoute responses, OpenAiRoute chatCompletions,
    AtomicReference<ResponsesCapabilityCache.Support> responsesSupport, boolean preferResponses
  ) {
    this.hideReasoningBlocks = hideReasoningBlocks;
    this.metrics = Objects.requireNonNull(metrics);
    this.metricsPurpose = Objects.requireNonNull(metricsPurpose);
    this.responses = Objects.requireNonNull(responses);
    this.chatCompletions = Objects.requireNonNull(chatCompletions);
    this.responsesSupport = Objects.requireNonNull(responsesSupport);
    this.preferResponses = preferResponses;
  }

  @Override
  public String chat(List<Message> messages, Map<String, Object> options, int timeoutSeconds)
    throws IOException, InterruptedException {
    long started = System.nanoTime();
    OpenAiRouteResult result = execute(messages, options, timeoutSeconds);
    metrics.recordRequest(metricsPurpose, result.outputTokens(), Duration.ofNanos(System.nanoTime() - started));
    return stripReasoningBlocks(result.content());
  }

  private OpenAiRouteResult execute(List<Message> messages, Map<String, Object> options, int timeoutSeconds)
    throws IOException, InterruptedException {
    if (!preferResponses || responsesSupport.get() == ResponsesCapabilityCache.Support.UNSUPPORTED) {
      return chatCompletions.execute(messages, options, timeoutSeconds);
    }
    try {
      OpenAiRouteResult result = responses.execute(messages, options, timeoutSeconds);
      responsesSupport.set(ResponsesCapabilityCache.Support.SUPPORTED);
      return result;
    } catch (UnsupportedResponsesEndpointException _) {
      responsesSupport.set(ResponsesCapabilityCache.Support.UNSUPPORTED);
      return chatCompletions.execute(messages, options, timeoutSeconds);
    }
  }

  String stripReasoningBlocks(String content) {
    if (!hideReasoningBlocks || content == null || content.isBlank()) {
      return content;
    }
    return REASONING_PATTERN.matcher(content).replaceAll("").trim();
  }

}
