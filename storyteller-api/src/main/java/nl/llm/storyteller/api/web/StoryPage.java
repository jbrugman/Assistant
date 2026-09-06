package nl.llm.storyteller.api.web;

import nl.llm.storyteller.api.persistence.SessionRecord;
import nl.llm.storyteller.core.model.Message;

import java.util.ArrayList;
import java.util.List;

public record StoryPage(SessionRecord session, List<StoryExchange> exchanges) {
  public static StoryPage from(SessionRecord session, List<Message> messages) {
    List<StoryExchange> exchanges = new ArrayList<>();
    for (int index = 0; index + 1 < messages.size(); index += 2) {
      exchanges.add(new StoryExchange(messages.get(index).content(), messages.get(index + 1).content()));
    }
    return new StoryPage(session, List.copyOf(exchanges));
  }
}
