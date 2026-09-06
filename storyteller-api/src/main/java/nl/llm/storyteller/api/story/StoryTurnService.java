package nl.llm.storyteller.api.story;

import nl.llm.storyteller.api.persistence.StoryRepository;
import nl.llm.storyteller.api.persistence.StoryTurnRecord;
import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.model.StoryChatPromptInput;
import nl.llm.storyteller.core.model.ValidationPromptInput;
import nl.llm.storyteller.core.service.ChatClient;
import nl.llm.storyteller.core.service.PromptResourceLoader;
import nl.llm.storyteller.core.service.PromptTemplateService;
import nl.llm.storyteller.core.service.ResponseGuard;
import nl.llm.storyteller.core.service.StoryChatPromptBuilder;
import nl.llm.storyteller.core.service.ValidationPromptBuilder;

import java.io.IOException;
import java.time.Clock;

public final class StoryTurnService {
  private static final int MAX_PROMPT_LENGTH = 100_000;

  private final StoryRepository repository;
  private final AppConfig config;
  private final ChatClient chatClient;
  private final ResponseGuard responseGuard;
  private final StoryChatPromptBuilder storyPromptBuilder;
  private final ValidationPromptBuilder validationPromptBuilder;
  private final Clock clock;

  public StoryTurnService(
    StoryRepository repository,
    AppConfig config,
    ChatClient chatClient,
    ChatClient validationClient
  ) {
    this(repository, config, chatClient, validationClient, Clock.systemUTC());
  }

  StoryTurnService(
    StoryRepository repository,
    AppConfig config,
    ChatClient chatClient,
    ChatClient validationClient,
    Clock clock
  ) {
    PromptResourceLoader resources = new PromptResourceLoader(config);
    PromptTemplateService templates = new PromptTemplateService(resources);
    this.repository = repository;
    this.config = config;
    this.chatClient = chatClient;
    this.responseGuard = new ResponseGuard(validationClient, config);
    this.storyPromptBuilder = new StoryChatPromptBuilder(resources, templates);
    this.validationPromptBuilder = new ValidationPromptBuilder(resources, templates);
    this.clock = clock;
  }

  public synchronized StoryTurnResult execute(String sessionId, String prompt)
    throws IOException, InterruptedException {
    String userInput = normalizePrompt(prompt);
    var recentMessages = repository.loadRecentMessages(sessionId, config.maxRecentTurns() * 2);
    String draftResponse = chatClient.chat(
      storyPromptBuilder.build(new StoryChatPromptInput(
        userInput, "", "", "", "", recentMessages, ""
      )),
      config.chatOptions(),
      config.requestTimeoutSeconds()
    );
    String response = validate(userInput, draftResponse);
    StoryTurnRecord stored = repository.appendTurn(sessionId, userInput, response, clock.instant());
    return new StoryTurnResult(stored.userMessageIndex(), stored.assistantMessageIndex(), response);
  }

  private String validate(String userInput, String draftResponse) throws InterruptedException {
    if (!config.validationEnabled()) {
      return responseGuard.validate("", "", draftResponse);
    }
    String systemPrompt = validationPromptBuilder.buildSystemPrompt();
    String request = validationPromptBuilder.buildRequest(new ValidationPromptInput(userInput, draftResponse, ""));
    return responseGuard.validate(systemPrompt, request, draftResponse);
  }

  private String normalizePrompt(String prompt) {
    if (prompt == null || prompt.isBlank()) {
      throw new IllegalArgumentException("Prompt must not be blank.");
    }
    String normalized = prompt.trim();
    if (normalized.length() > MAX_PROMPT_LENGTH) {
      throw new IllegalArgumentException("Prompt must not exceed 100000 characters.");
    }
    return normalized;
  }
}
