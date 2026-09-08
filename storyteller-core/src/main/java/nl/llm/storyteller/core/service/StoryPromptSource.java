package nl.llm.storyteller.core.service;

@FunctionalInterface
public interface StoryPromptSource {
  StoryPrompts load();
}
