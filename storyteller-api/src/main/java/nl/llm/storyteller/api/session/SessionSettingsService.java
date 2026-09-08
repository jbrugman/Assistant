package nl.llm.storyteller.api.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import nl.llm.storyteller.core.JsonSupport;
import nl.llm.storyteller.core.graph.KnowledgeGraphValidator;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphJsonCodec;
import nl.llm.storyteller.db.SessionPrompts;
import nl.llm.storyteller.db.SessionSettings;
import nl.llm.storyteller.db.SessionSettingsRepository;

import static nl.llm.storyteller.api.input.TextInputNormalizer.requiredMultiline;

public final class SessionSettingsService {
  private static final int MAX_PROMPT_LENGTH = 1_000_000;
  private final SessionSettingsRepository repository;
  private final KnowledgeGraphValidator graphValidator;
  private final KnowledgeGraphJsonCodec graphCodec = new KnowledgeGraphJsonCodec();

  public SessionSettingsService(SessionSettingsRepository repository, KnowledgeGraphValidator graphValidator) {
    this.repository = repository;
    this.graphValidator = graphValidator;
  }

  public SessionSettings load(String sessionId) {
    return repository.load(sessionId);
  }

  public void save(
    String sessionId,
    String systemPrompt,
    String fixedProtagonists,
    String rules,
    String knowledgeGraph
  ) {
    SessionPrompts prompts = new SessionPrompts(
      requiredMultiline(systemPrompt, "System prompt", MAX_PROMPT_LENGTH),
      requiredMultiline(fixedProtagonists, "Fixed protagonists", MAX_PROMPT_LENGTH),
      requiredMultiline(rules, "Rules", MAX_PROMPT_LENGTH)
    );
    FixedProtagonistsValidator.validate(prompts.fixedProtagonists());
    repository.save(sessionId, new SessionSettings(prompts, parseKnowledgeGraph(knowledgeGraph)));
  }

  public String formatKnowledgeGraph(SessionSettings settings) {
    try {
      return JsonSupport.OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
        .writeValueAsString(graphCodec.toJson(settings.knowledgeGraph()));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Could not render the knowledge graph.", ex);
    }
  }

  private KnowledgeGraphDocument parseKnowledgeGraph(String knowledgeGraph) {
    String normalized = requiredMultiline(knowledgeGraph, "Knowledge graph", MAX_PROMPT_LENGTH);
    try {
      KnowledgeGraphDocument document = graphCodec.fromJson(normalized);
      graphValidator.validate(document);
      return document;
    } catch (JsonProcessingException | IllegalArgumentException ex) {
      throw new InvalidKnowledgeGraphException("Invalid knowledge graph: " + ex.getMessage(), ex);
    }
  }
}
