package nl.llm.storyteller.api.web;

import nl.llm.storyteller.db.SessionPrompts;
import nl.llm.storyteller.api.session.InvalidFixedProtagonistsException;
import nl.llm.storyteller.api.session.InvalidKnowledgeGraphException;
import nl.llm.storyteller.db.SessionPrompts;

public record StorySettingsPage(
  SessionPrompts prompts,
  String knowledgeGraph,
  String yamlError,
  int yamlErrorLine,
  int yamlErrorColumn,
  String knowledgeGraphError
) {
  static StorySettingsPage valid(SessionPrompts prompts, String knowledgeGraph) {
    return new StorySettingsPage(prompts, knowledgeGraph, "", 0, 0, "");
  }

  static StorySettingsPage invalidYaml(
    SessionPrompts prompts,
    String knowledgeGraph,
    InvalidFixedProtagonistsException error
  ) {
    return new StorySettingsPage(prompts, knowledgeGraph, error.getMessage(), error.line(), error.column(), "");
  }

  static StorySettingsPage invalidKnowledgeGraph(
    SessionPrompts prompts,
    String knowledgeGraph,
    InvalidKnowledgeGraphException error
  ) {
    return new StorySettingsPage(prompts, knowledgeGraph, "", 0, 0, error.getMessage());
  }

  public boolean hasYamlError() {
    return !yamlError.isEmpty();
  }

  public boolean hasKnowledgeGraphError() {
    return !knowledgeGraphError.isEmpty();
  }
}
