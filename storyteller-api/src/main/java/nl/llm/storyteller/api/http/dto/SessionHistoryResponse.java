package nl.llm.storyteller.api.http.dto;

import nl.llm.storyteller.db.StoryMessageRecord;

import java.util.List;

public record SessionHistoryResponse(String sessionId, List<StoryMessageRecord> messages) { }
