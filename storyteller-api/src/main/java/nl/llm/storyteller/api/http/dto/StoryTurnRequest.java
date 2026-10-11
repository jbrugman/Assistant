package nl.llm.storyteller.api.http.dto;

import java.util.List;

public record StoryTurnRequest(String prompt, List<Integer> pastMessageIndexes) {
}
