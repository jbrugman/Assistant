package nl.llm.storyteller.core.service;

import java.util.Objects;

public record StoryPrompts(
  String systemPrompt,
  String fixedProtagonists,
  String rules
) {
  public StoryPrompts {
    Objects.requireNonNull(systemPrompt, "systemPrompt");
    Objects.requireNonNull(fixedProtagonists, "fixedProtagonists");
    Objects.requireNonNull(rules, "rules");
  }
}
