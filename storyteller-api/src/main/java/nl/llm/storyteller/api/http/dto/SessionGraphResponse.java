package nl.llm.storyteller.api.http.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record SessionGraphResponse(String sessionId, JsonNode knowledgeGraph) { }
