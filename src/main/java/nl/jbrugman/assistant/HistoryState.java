package nl.jbrugman.assistant;

import java.util.ArrayList;
import java.util.List;

record HistoryState(List<Message> messages, int summaryCursor, int canonicalStateCursor) {
    static HistoryState empty() {
        return new HistoryState(new ArrayList<>(), 0, 0);
    }
}
