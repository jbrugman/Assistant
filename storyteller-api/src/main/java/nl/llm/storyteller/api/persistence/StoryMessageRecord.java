package nl.llm.storyteller.api.persistence;

public record StoryMessageRecord(int messageIndex, String role, String content, boolean hasImage) { }
