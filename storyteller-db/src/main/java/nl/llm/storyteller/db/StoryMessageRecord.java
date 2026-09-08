package nl.llm.storyteller.db;

public record StoryMessageRecord(int messageIndex, String role, String content, boolean hasImage) { }
