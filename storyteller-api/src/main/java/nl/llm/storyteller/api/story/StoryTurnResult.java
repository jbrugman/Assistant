package nl.llm.storyteller.api.story;

public record StoryTurnResult(int userMessageIndex, int assistantMessageIndex, String response) {
}
