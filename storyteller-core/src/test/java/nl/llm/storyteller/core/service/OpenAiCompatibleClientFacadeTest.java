package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.service.openai.OpenAiRoute;
import nl.llm.storyteller.core.service.openai.OpenAiRouteResult;
import nl.llm.storyteller.core.service.openai.UnsupportedResponsesEndpointException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class OpenAiCompatibleClientFacadeTest {
  @Test
  void keepsCapabilityStateSeparateForDifferentChatAndMemoryServers() {
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
  void stripsReasoningBlocks(String response, String expected) {
    OpenAiCompatibleClientFacade facade = new OpenAiCompatibleClientFacade(
      true, ChatRequestMetrics.NONE, "test", (_, _, _) -> new OpenAiRouteResult(response, 1),
      (_, _, _) -> new OpenAiRouteResult("fallback", 1)
    );

    assertEquals(expected, facade.stripReasoningBlocks(response));
  }

  @Test
  void cachesUnsupportedResponsesRouteAndFallsBackToChatCompletions() throws Exception {
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
  void keepsUsingResponsesAfterSuccessfulCapabilityCheck() throws Exception {
    AtomicInteger responsesCalls = new AtomicInteger();
    OpenAiRoute responses = (_, _, _) -> new OpenAiRouteResult("response-" + responsesCalls.incrementAndGet(), 1);
    OpenAiCompatibleClientFacade facade = new OpenAiCompatibleClientFacade(
      true, ChatRequestMetrics.NONE, "test", responses,
      (_, _, _) -> new OpenAiRouteResult("fallback", 1)
    );

    assertEquals("response-1", facade.chat(List.of(new Message("user", "one")), Map.of(), 10));
    assertEquals("response-2", facade.chat(List.of(new Message("user", "two")), Map.of(), 10));
  }
}
