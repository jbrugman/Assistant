package nl.llm.storyteller.api.story;

import nl.llm.storyteller.api.persistence.SessionPromptRepository;
import nl.llm.storyteller.api.persistence.SessionPrompts;
import nl.llm.storyteller.api.persistence.StoryRepository;
import nl.llm.storyteller.api.persistence.StoryImage;
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
import java.util.ArrayList;
import java.util.List;
import nl.llm.storyteller.core.model.Message;

import static nl.llm.storyteller.api.input.TextInputNormalizer.requiredMultiline;

public final class StoryTurnService {
  private static final int MAX_PROMPT_LENGTH = 100_000;

  private final StoryRepository repository;
  private final SessionPromptRepository promptRepository;
  private final AppConfig config;
  private final ChatClient chatClient;
  private final ResponseGuard responseGuard;
  private final StoryChatPromptBuilder storyPromptBuilder;
  private final ValidationPromptBuilder validationPromptBuilder;
  private final Clock clock;

  public StoryTurnService(
    StoryRepository repository,
    SessionPromptRepository promptRepository,
    AppConfig config,
    ChatClient chatClient,
    ChatClient validationClient
  ) {
    this(repository, promptRepository, config, chatClient, validationClient, Clock.systemUTC());
  }

  StoryTurnService(
    StoryRepository repository,
    SessionPromptRepository promptRepository,
    AppConfig config,
    ChatClient chatClient,
    ChatClient validationClient,
    Clock clock
  ) {
    PromptResourceLoader resources = new PromptResourceLoader(config);
    PromptTemplateService templates = new PromptTemplateService(resources);
    this.repository = repository;
    this.promptRepository = promptRepository;
    this.config = config;
    this.chatClient = chatClient;
    this.responseGuard = new ResponseGuard(validationClient, config);
    this.storyPromptBuilder = new StoryChatPromptBuilder(resources, templates);
    this.validationPromptBuilder = new ValidationPromptBuilder(resources, templates);
    this.clock = clock;
  }

  public synchronized StoryTurnResult execute(String sessionId, String prompt)
    throws IOException, InterruptedException {
    return execute(sessionId, prompt, null);
  }

  public synchronized StoryTurnResult execute(String sessionId, String prompt, StoryImage image)
    throws IOException, InterruptedException {
    String userInput = normalizePrompt(prompt);
    SessionPrompts prompts = promptRepository.load(sessionId);
    var recentMessages = repository.loadRecentMessages(sessionId, config.maxRecentTurns() * 2);
    List<Message> messages = new ArrayList<>(storyPromptBuilder.build(new StoryChatPromptInput(
      userInput, "", "", "", "", recentMessages, ""
    ), prompts.systemPrompt(), prompts.fixedProtagonists()));
    if (image != null) {
      Message userMessage = messages.getLast();
      messages.set(messages.size() - 1, Message.withImage(
        userMessage.role(), userMessage.content(), image.dataUrl()
      ));
    }
    String draftResponse = chatClient.chat(
      messages,
      config.chatOptions(),
      config.requestTimeoutSeconds()
    );
    String response = validate(userInput, draftResponse, prompts);
    StoryTurnRecord stored = repository.appendTurn(sessionId, userInput, response, image, clock.instant());
    return new StoryTurnResult(stored.userMessageIndex(), stored.assistantMessageIndex(), response);
  }

  public synchronized void undoLastTurn(String sessionId) {
    repository.undoLastTurn(sessionId, clock.instant());
  }

  private String validate(String userInput, String draftResponse, SessionPrompts prompts)
    throws InterruptedException {
    if (!config.validationEnabled()) {
      return responseGuard.validate("", "", draftResponse);
    }
    String systemPrompt = validationPromptBuilder.buildSystemPrompt();
    String request = validationPromptBuilder.buildRequest(
      new ValidationPromptInput(userInput, draftResponse, ""),
      prompts.rules(),
      prompts.fixedProtagonists()
    );
    return responseGuard.validate(systemPrompt, request, draftResponse);
  }

  private String normalizePrompt(String prompt) {
    return requiredMultiline(prompt, "Prompt", MAX_PROMPT_LENGTH);
  }
}
