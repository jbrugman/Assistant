package nl.llm.storyteller.api.persistence;

public record StoryTurnRecord(int userMessageIndex, int assistantMessageIndex) {
}
