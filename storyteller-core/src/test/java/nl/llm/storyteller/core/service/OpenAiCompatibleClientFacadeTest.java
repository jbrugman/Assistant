package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.service.openai.OpenAiRoute;
import nl.llm.storyteller.core.service.openai.OpenAiRouteResult;
import nl.llm.storyteller.core.service.openai.UnsupportedResponsesEndpointException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class OpenAiCompatibleClientFacadeTest {
  @Test
  @DisplayName("""
    Given different chat and memory backend URLs,
    When their Responses capability state is requested,
    Then each backend should have an independent cached state
    """)
  void shouldKeepCapabilityStateSeparateForDifferentChatAndMemoryServers() {
    ResponsesCapabilityCache cache = new ResponsesCapabilityCache();

    assertNotSame(
      cache.forEndpoint("http://chat.example/v1/chat/completions"),
      cache.forEndpoint("http://memory.example/v1/chat/completions")
    );
    assertSame(
      cache.forEndpoint("http://chat.example/v1/chat/completions"),
      cache.forEndpoint("http://chat.example/v1/chat/completions")
    );
  }

  @ParameterizedTest
  @CsvSource(value = {
    "<think>hidden</think>Visible;Visible",
    "<|thinking|>hidden</|thinking|>Visible;Visible",
    "<|channel>thought hidden<channel|>Visible;Visible"
  }, delimiter = ';')
  @DisplayName("""
    Given model output containing a reasoning block,
    When the facade sanitizes the response,
    Then only visible output should remain
    """)
  void shouldStripReasoningBlocks(String response, String expected) {
    OpenAiCompatibleClientFacade facade = new OpenAiCompatibleClientFacade(
      true, ChatRequestMetrics.NONE, "test", (_, _, _) -> new OpenAiRouteResult(response, 1),
      (_, _, _) -> new OpenAiRouteResult("fallback", 1)
    );

    assertEquals(expected, facade.stripReasoningBlocks(response));
  }

  @Test
  @DisplayName("""
    Given a backend without a Responses endpoint,
    When multiple requests are submitted,
    Then the facade should cache the failure and use Chat Completions
    """)
  void shouldCacheUnsupportedResponsesRouteAndFallBackToChatCompletions() throws Exception {
    AtomicInteger responsesCalls = new AtomicInteger();
    AtomicInteger chatCalls = new AtomicInteger();
    OpenAiRoute responses = (_, _, _) -> {
      responsesCalls.incrementAndGet();
      throw new UnsupportedResponsesEndpointException("not supported");
    };
    OpenAiRoute chat = (_, _, _) -> {
      chatCalls.incrementAndGet();
      return new OpenAiRouteResult("chat", 1);
    };
    OpenAiCompatibleClientFacade facade = new OpenAiCompatibleClientFacade(
      true, ChatRequestMetrics.NONE, "test", responses, chat
    );

    assertEquals("chat", facade.chat(List.of(new Message("user", "one")), Map.of(), 10));
    assertEquals("chat", facade.chat(List.of(new Message("user", "two")), Map.of(), 10));
    assertEquals(1, responsesCalls.get());
    assertEquals(2, chatCalls.get());
  }

  @Test
  @DisplayName("""
    Given a backend supporting Responses,
    When multiple requests are submitted,
    Then the facade should continue using the Responses route
    """)
  void shouldKeepUsingResponsesAfterSuccessfulCapabilityCheck() throws Exception {
    AtomicInteger responsesCalls = new AtomicInteger();
    OpenAiRoute responses = (_, _, _) -> new OpenAiRouteResult("response-" + responsesCalls.incrementAndGet(), 1);
    OpenAiCompatibleClientFacade facade = new OpenAiCompatibleClientFacade(
      true, ChatRequestMetrics.NONE, "test", responses,
      (_, _, _) -> new OpenAiRouteResult("fallback", 1)
    );

    assertEquals("response-1", facade.chat(List.of(new Message("user", "one")), Map.of(), 10));
    assertEquals("response-2", facade.chat(List.of(new Message("user", "two")), Map.of(), 10));
  }

  @Test
  @DisplayName("""
    Given no explicit model selection,
    When a request is submitted,
    Then the facade should use Chat Completions directly
    """)
  void shouldUseChatCompletionsDirectlyWithoutExplicitModelSelection() throws Exception {
    AtomicInteger responsesCalls = new AtomicInteger();
    OpenAiCompatibleClientFacade facade = new OpenAiCompatibleClientFacade(
      true, ChatRequestMetrics.NONE, "test",
      (_, _, _) -> {
        responsesCalls.incrementAndGet();
        return new OpenAiRouteResult("responses", 1);
      },
      (_, _, _) -> new OpenAiRouteResult("chat", 1),
      new java.util.concurrent.atomic.AtomicReference<>(ResponsesCapabilityCache.Support.UNKNOWN), false
    );

    assertEquals("chat", facade.chat(List.of(new Message("user", "hello")), Map.of(), 10));
    assertEquals(0, responsesCalls.get());
  }

  @Test
  @DisplayName("""
    Given an oMLX background-memory backend,
    When a request is submitted with reasoning disabled,
    Then Gemma thinking should also be disabled through the chat template arguments
    """)
  void shouldDisableOmlxThinkingForBackgroundRequests() throws Exception {
    AtomicReference<Map<String, Object>> suppliedOptions = new AtomicReference<>();
    OpenAiRoute responses = (_, options, _) -> {
      suppliedOptions.set(options);
      return new OpenAiRouteResult("OK", 1);
    };
    OpenAiCompatibleClientFacade facade = new OpenAiCompatibleClientFacade(
      true,
      ChatRequestMetrics.NONE,
      "derived-state",
      responses,
      (_, _, _) -> new OpenAiRouteResult("fallback", 1),
      new AtomicReference<>(ResponsesCapabilityCache.Support.UNKNOWN),
      true,
      true,
      () -> true
    );

    facade.chat(List.of(new Message("user", "hello")), Map.of(
      "temperature", 0,
      "chat_template_kwargs", Map.of("preserve_thinking", true)
    ), 10);

    assertEquals(Map.of("enable_thinking", false, "preserve_thinking", true),
      suppliedOptions.get().get("chat_template_kwargs"));
    assertEquals(0, suppliedOptions.get().get("temperature"));
  }

  @Test
  @DisplayName("""
    Given a background backend whose model-list probe is unavailable,
    When a request is submitted,
    Then optional backend detection should not block the model request
    """)
  void shouldContinueWhenBackendDetectionIsUnavailable() throws Exception {
    AtomicReference<Map<String, Object>> suppliedOptions = new AtomicReference<>();
    OpenAiRoute responses = (_, options, _) -> {
      suppliedOptions.set(options);
      return new OpenAiRouteResult("OK", 1);
    };
    OpenAiCompatibleClientFacade facade = new OpenAiCompatibleClientFacade(
      true,
      ChatRequestMetrics.NONE,
      "derived-state",
      responses,
      (_, _, _) -> new OpenAiRouteResult("fallback", 1),
      new AtomicReference<>(ResponsesCapabilityCache.Support.UNKNOWN),
      true,
      true,
      () -> {
        throw new java.io.IOException("models unavailable");
      }
    );

    assertEquals("OK", facade.chat(List.of(new Message("user", "hello")), Map.of(), 10));
    assertEquals(Map.of(), suppliedOptions.get());
  }

  @Test
  @DisplayName("""
    Given a request-specific model purpose,
    When the facade records completed-request metrics,
    Then it should retain that specific purpose and the returned output-token count
    """)
  void shouldRecordRequestSpecificPurpose() throws Exception {
    AtomicReference<String> recordedPurpose = new AtomicReference<>();
    AtomicLong recordedTokens = new AtomicLong();
    ChatRequestMetrics metrics = (purpose, tokens, _) -> {
      recordedPurpose.set(purpose);
      recordedTokens.set(tokens);
    };
    OpenAiCompatibleClientFacade facade = new OpenAiCompatibleClientFacade(
      true, metrics, "derived-state", (_, _, _) -> new OpenAiRouteResult("OK", 17, 3),
      (_, _, _) -> new OpenAiRouteResult("fallback", 1)
    );

    facade.chat("short-memory", List.of(new Message("user", "hello")), Map.of(), 10);

    assertEquals("short-memory", recordedPurpose.get());
    assertEquals(17, recordedTokens.get());
  }
}
