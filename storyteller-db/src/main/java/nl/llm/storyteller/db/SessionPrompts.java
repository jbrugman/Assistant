package nl.llm.storyteller.db;

import java.util.Objects;

public record SessionPrompts(
  String systemPrompt,
  String fixedProtagonists,
  String rules
) {
  public static final String SYSTEM_PROMPT_NAME = "systemprompt.md";
  public static final String FIXED_PROTAGONISTS_NAME = "fixed_protagonists.yml";
  public static final String RULES_NAME = "rules.md";

  public SessionPrompts {
    Objects.requireNonNull(systemPrompt, "systemPrompt");
    Objects.requireNonNull(fixedProtagonists, "fixedProtagonists");
    Objects.requireNonNull(rules, "rules");
  }

  public static SessionPrompts empty() {
    return new SessionPrompts("", "", "");
  }
}
