package nl.llm.storyteller.core.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import nl.llm.storyteller.core.JsonSupport;
import nl.llm.storyteller.core.model.TurnState;

public final class TurnStateJsonCodec {
  public static final String TRIGGER_WORD = "trigger_word";
  public static final String STARTED = "started";
  public static final String ROUND_NUMBER = "round_number";
  public static final String PROTAGONISTS = "protagonists";
  public static final String TURNS_THIS_ROUND = "turns_this_round";

  private TurnStateJsonCodec() { }

  public static ObjectNode toJson(TurnState state) {
    ObjectNode data = JsonSupport.OBJECT_MAPPER.createObjectNode();
    data.put(TRIGGER_WORD, state.triggerWord());
    data.put(STARTED, state.started());
    data.put(ROUND_NUMBER, state.roundNumber());
    data.set(PROTAGONISTS, JsonSupport.OBJECT_MAPPER.valueToTree(state.protagonists()));
    data.set(TURNS_THIS_ROUND, JsonSupport.OBJECT_MAPPER.valueToTree(state.turnsThisRound()));
    return data;
  }
}
