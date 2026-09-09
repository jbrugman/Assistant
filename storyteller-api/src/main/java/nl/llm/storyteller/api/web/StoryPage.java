package nl.llm.storyteller.api.web;

import nl.llm.storyteller.db.SessionRecord;
import nl.llm.storyteller.db.StoryMessageRecord;

import java.util.ArrayList;
import java.util.List;

public record StoryPage(
  SessionRecord session,
  List<StoryExchange> exchanges,
  boolean hasOlder,
  String submittedPrompt,
  String errorMessage
) {
  public static StoryPage from(
    SessionRecord session,
    List<StoryMessageRecord> messages,
    int referenceBeforeMessageIndex
  ) {
    List<StoryExchange> exchanges = new ArrayList<>();
    for (int index = 0; index + 1 < messages.size(); index += 2) {
      exchanges.add(new StoryExchange(
        messages.get(index).messageIndex(),
        messages.get(index).content(),
        messages.get(index + 1).content(),
        messages.get(index).hasImage(),
        messages.get(index).messageIndex() < referenceBeforeMessageIndex
      ));
    }
    boolean hasOlder = !messages.isEmpty() && messages.getFirst().messageIndex() > 0;
    return new StoryPage(session, List.copyOf(exchanges), hasOlder, "", "");
  }

  public StoryPage withBackendError(String prompt) {
    return new StoryPage(
      session,
      exchanges,
      hasOlder,
      prompt == null ? "" : prompt,
      "The model is temporarily unavailable. Your prompt was not saved; please try again."
    );
  }
}
