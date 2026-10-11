package nl.llm.storyteller.api.http.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record SessionLifetimeRequest(JsonNode infinite) { }
