package nl.llm.storyteller.model;

import java.util.List;

public record GameModeDefinition(
    String triggerWord,
    List<String> protagonists
) {
    public boolean isValidTurnBasedMode() {
        return triggerWord != null && !triggerWord.isBlank() && protagonists != null && !protagonists.isEmpty();
    }
}
