package nl.llm.storyteller.api.http.dto;

public record EditAssistantMessageResponse(String sessionId, int messageIndex, String content) {
}
