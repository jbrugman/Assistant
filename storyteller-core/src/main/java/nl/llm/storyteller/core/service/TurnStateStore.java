package nl.llm.storyteller.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import nl.llm.storyteller.core.JsonSupport;
import nl.llm.storyteller.core.model.TurnState;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static nl.llm.storyteller.core.service.TurnStateJsonCodec.PROTAGONISTS;
import static nl.llm.storyteller.core.service.TurnStateJsonCodec.ROUND_NUMBER;
import static nl.llm.storyteller.core.service.TurnStateJsonCodec.STARTED;
import static nl.llm.storyteller.core.service.TurnStateJsonCodec.TRIGGER_WORD;
import static nl.llm.storyteller.core.service.TurnStateJsonCodec.TURNS_THIS_ROUND;

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
      String triggerWord = data.path(TRIGGER_WORD).asText("");
      boolean started = data.path(STARTED).asBoolean(false);
      int roundNumber = data.path(ROUND_NUMBER).asInt(0);

      List<String> protagonists = JsonSupport.OBJECT_MAPPER.convertValue(
        data.path(PROTAGONISTS),
        JsonSupport.OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, String.class)
      );

      Map<String, Integer> turnsThisRound = new LinkedHashMap<>();
      JsonNode turnsNode = data.path(TURNS_THIS_ROUND);
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
    try {
      Path parent = path.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      JsonSupport.OBJECT_MAPPER.writeValue(path.toFile(), TurnStateJsonCodec.toJson(state));
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
