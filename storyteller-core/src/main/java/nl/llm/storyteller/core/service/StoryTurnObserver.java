package nl.llm.storyteller.core.service;

@FunctionalInterface
public interface StoryTurnObserver {
  StoryTurnObserver NONE = (_, _, _) -> { };

  void completed(String userInput, String draftResponse, String finalResponse);
}
