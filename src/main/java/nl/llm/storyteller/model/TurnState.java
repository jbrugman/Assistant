package nl.llm.storyteller.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TurnState(
    String triggerWord,
    boolean started,
    int roundNumber,
    List<String> protagonists,
    Map<String, Integer> turnsThisRound
) {
    public static TurnState inactive() {
        return new TurnState("", false, 0, List.of(), Map.of());
    }

    public static TurnState started(String triggerWord, List<String> protagonists) {
        Map<String, Integer> turns = new LinkedHashMap<>();
        for (String protagonist : protagonists) {
            turns.put(protagonist, 0);
        }
        return new TurnState(triggerWord, true, 1, List.copyOf(protagonists), Map.copyOf(turns));
    }
}
