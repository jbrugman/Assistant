package nl.llm.storyteller.api.web;

import nl.llm.storyteller.api.persistence.SessionPrompts;
import nl.llm.storyteller.api.session.InvalidFixedProtagonistsException;

public record StorySettingsPage(
  SessionPrompts prompts,
  String yamlError,
  int yamlErrorLine,
  int yamlErrorColumn
) {
  static StorySettingsPage valid(SessionPrompts prompts) {
    return new StorySettingsPage(prompts, "", 0, 0);
  }

  static StorySettingsPage invalid(SessionPrompts prompts, InvalidFixedProtagonistsException error) {
    return new StorySettingsPage(prompts, error.getMessage(), error.line(), error.column());
  }

  public boolean hasYamlError() {
    return !yamlError.isEmpty();
  }
}
