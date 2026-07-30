package nl.llm.storyteller.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import nl.llm.storyteller.JsonSupport;
import nl.llm.storyteller.model.TurnState;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TurnStateStore {
    private final Path path;

    public TurnStateStore(Path path) {
        this.path = path;
    }

    public synchronized TurnState load() {
        if (!Files.exists(path)) {
            return TurnState.inactive();
        }

        try {
            JsonNode data = JsonSupport.OBJECT_MAPPER.readTree(Files.readString(path, StandardCharsets.UTF_8));
            String triggerWord = data.path("trigger_word").asText("");
            boolean started = data.path("started").asBoolean(false);
            int roundNumber = data.path("round_number").asInt(0);

            List<String> protagonists = JsonSupport.OBJECT_MAPPER.convertValue(
                data.path("protagonists"),
                JsonSupport.OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, String.class)
            );

            Map<String, Integer> turnsThisRound = new LinkedHashMap<>();
            JsonNode turnsNode = data.path("turns_this_round");
            if (turnsNode.isObject()) {
                Iterator<String> fieldNames = turnsNode.fieldNames();
                while (fieldNames.hasNext()) {
                    String fieldName = fieldNames.next();
                    turnsThisRound.put(fieldName, turnsNode.path(fieldName).asInt(0));
                }
            }

            return new TurnState(triggerWord, started, roundNumber, protagonists, Map.copyOf(turnsThisRound));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid JSON in " + path + ": " + ex.getOriginalMessage(), ex);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public synchronized void save(TurnState state) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trigger_word", state.triggerWord());
        data.put("started", state.started());
        data.put("round_number", state.roundNumber());
        data.put("protagonists", state.protagonists());
        data.put("turns_this_round", state.turnsThisRound());

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonSupport.OBJECT_MAPPER.writeValue(path.toFile(), data);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
