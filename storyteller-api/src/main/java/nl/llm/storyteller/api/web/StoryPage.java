package nl.llm.storyteller.api.web;

import nl.llm.storyteller.api.persistence.SessionRecord;
import nl.llm.storyteller.api.persistence.StoryMessageRecord;

import java.util.ArrayList;
import java.util.List;

public record StoryPage(SessionRecord session, List<StoryExchange> exchanges, boolean hasOlder) {
  public static StoryPage from(SessionRecord session, List<StoryMessageRecord> messages) {
    List<StoryExchange> exchanges = new ArrayList<>();
    for (int index = 0; index + 1 < messages.size(); index += 2) {
      exchanges.add(new StoryExchange(
        messages.get(index).messageIndex(),
        messages.get(index).content(),
        messages.get(index + 1).content()
      ));
    }
    boolean hasOlder = !messages.isEmpty() && messages.getFirst().messageIndex() > 0;
    return new StoryPage(session, List.copyOf(exchanges), hasOlder);
  }
}
