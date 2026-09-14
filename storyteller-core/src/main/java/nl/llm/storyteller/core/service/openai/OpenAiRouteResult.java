package nl.llm.storyteller.core.service.openai;

public record OpenAiRouteResult(String content, long outputTokens, long reasoningTokens) {
  public OpenAiRouteResult(String content, long outputTokens) {
    this(content, outputTokens, -1);
  }
}
