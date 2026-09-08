package nl.llm.storyteller.api.session;

import nl.llm.storyteller.api.persistence.SessionPromptRepository;
import nl.llm.storyteller.api.persistence.SessionPrompts;

import static nl.llm.storyteller.api.input.TextInputNormalizer.requiredMultiline;

public final class SessionPromptService {
  private static final int MAX_PROMPT_LENGTH = 1_000_000;
  private final SessionPromptRepository repository;

  public SessionPromptService(SessionPromptRepository repository) {
    this.repository = repository;
  }

  public SessionPrompts load(String sessionId) {
    return repository.load(sessionId);
  }

  public void save(
    String sessionId,
    String systemPrompt,
    String fixedProtagonists,
    String rules
  ) {
    SessionPrompts prompts = new SessionPrompts(
      requiredMultiline(systemPrompt, "System prompt", MAX_PROMPT_LENGTH),
      requiredMultiline(fixedProtagonists, "Fixed protagonists", MAX_PROMPT_LENGTH),
      requiredMultiline(rules, "Rules", MAX_PROMPT_LENGTH)
    );
    FixedProtagonistsValidator.validate(prompts.fixedProtagonists());
    repository.save(sessionId, prompts);
  }
}
