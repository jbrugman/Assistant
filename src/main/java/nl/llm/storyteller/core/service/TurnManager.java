package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.AppConfig;
import nl.llm.storyteller.core.model.GameModeDefinition;
import nl.llm.storyteller.core.model.TurnRuleDecision;
import nl.llm.storyteller.core.model.TurnState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TurnManager {
  private static final String PARTY = "Party";
  private static final Pattern ACTOR_PREFIX_PATTERN = Pattern.compile("^\\(([^)]+)\\)");

  private final AppConfig config;
  private final PromptResourceLoader promptResourceLoader;
  private final PromptTemplateService promptTemplateService;
  private final GameModeDefinitionParser gameModeDefinitionParser;
  private final TurnStateStore turnStateStore;

  public TurnManager(
    AppConfig config,
    PromptResourceLoader promptResourceLoader,
    PromptTemplateService promptTemplateService,
    GameModeDefinitionParser gameModeDefinitionParser,
    TurnStateStore turnStateStore
  ) {
    this.config = config;
    this.promptResourceLoader = promptResourceLoader;
    this.promptTemplateService = promptTemplateService;
    this.gameModeDefinitionParser = gameModeDefinitionParser;
    this.turnStateStore = turnStateStore;
  }

  public TurnRuleDecision evaluate(String userInput) {
    if (!config.turnBasedModeEnabled()) {
      return TurnRuleDecision.none();
    }

    GameModeDefinition definition = gameModeDefinitionParser.parse(promptResourceLoader.loadFixedProtagonists());
    if (!definition.isValidTurnBasedMode()) {
      return TurnRuleDecision.none();
    }

    if (isStartTrigger(userInput, definition.triggerWord())) {
      turnStateStore.save(TurnState.started(definition.triggerWord(), definition.protagonists()));
      return TurnRuleDecision.none();
    }

    TurnState currentState = reconcileState(definition, turnStateStore.load());
    if (!currentState.started()) {
      turnStateStore.save(currentState);
      return TurnRuleDecision.none();
    }

    ActorScope actorScope = resolveActorScope(userInput, currentState.protagonists());
    if (actorScope == null) {
      turnStateStore.save(currentState);
      return TurnRuleDecision.none();
    }

    TurnState normalizedState = normalizeForNewRoundIfNeeded(currentState);
    boolean violation = isViolation(normalizedState, actorScope.actors());
    TurnState updatedState = markActors(normalizedState, actorScope.actors());
    turnStateStore.save(updatedState);

    return violation ? new TurnRuleDecision(buildInstruction(actorScope.scopeType())) : TurnRuleDecision.none();
  }

  private boolean isStartTrigger(String userInput, String triggerWord) {
    return userInput != null && triggerWord != null
      && userInput.trim().equalsIgnoreCase(triggerWord.trim());
  }

  private TurnState reconcileState(GameModeDefinition definition, TurnState storedState) {
    if (!storedState.started()
      || !definition.triggerWord().equals(storedState.triggerWord())
      || !definition.protagonists().equals(storedState.protagonists())) {
      return new TurnState(
        definition.triggerWord(),
        false,
        0,
        definition.protagonists(),
        zeroTurns(definition.protagonists())
      );
    }
    return storedState;
  }

  private Map<String, Integer> zeroTurns(List<String> protagonists) {
    Map<String, Integer> turns = new LinkedHashMap<>();
    for (String protagonist : protagonists) {
      turns.put(protagonist, 0);
    }
    return Map.copyOf(turns);
  }

  private ActorScope resolveActorScope(String userInput, List<String> protagonists) {
    if (userInput == null || userInput.isBlank()) {
      return null;
    }

    Matcher matcher = ACTOR_PREFIX_PATTERN.matcher(userInput.trim());
    if (!matcher.find()) {
      return null;
    }

    String actorName = matcher.group(1).trim();
    if (PARTY.equalsIgnoreCase(actorName)) {
      return new ActorScope(ScopeType.PARTY, protagonists);
    }

    for (String protagonist : protagonists) {
      if (protagonist.equalsIgnoreCase(actorName)) {
        return new ActorScope(ScopeType.SINGLE, List.of(protagonist));
      }
    }
    return null;
  }

  private TurnState normalizeForNewRoundIfNeeded(TurnState state) {
    boolean allActed = state.protagonists().stream()
      .allMatch(protagonist -> state.turnsThisRound().getOrDefault(protagonist, 0) > 0);
    if (!allActed) {
      return state;
    }

    return new TurnState(
      state.triggerWord(),
      true,
      state.roundNumber() + 1,
      state.protagonists(),
      zeroTurns(state.protagonists())
    );
  }

  private boolean isViolation(TurnState state, List<String> actors) {
    boolean actorAlreadyMoved = actors.stream().anyMatch(actor -> state.turnsThisRound().getOrDefault(actor, 0) > 0);
    if (!actorAlreadyMoved) {
      return false;
    }

    return state.protagonists().stream()
      .anyMatch(protagonist -> state.turnsThisRound().getOrDefault(protagonist, 0) == 0);
  }

  private TurnState markActors(TurnState state, List<String> actors) {
    Map<String, Integer> updatedTurns = new LinkedHashMap<>(state.turnsThisRound());
    for (String actor : actors) {
      updatedTurns.put(actor, updatedTurns.getOrDefault(actor, 0) + 1);
    }
    return new TurnState(
      state.triggerWord(),
      state.started(),
      state.roundNumber(),
      state.protagonists(),
      Map.copyOf(updatedTurns)
    );
  }

  private String buildInstruction(ScopeType scopeType) {
    if (scopeType == ScopeType.PARTY) {
      return promptTemplateService.buildTurnViolationPartyInstruction();
    }

    return promptTemplateService.buildTurnViolationSingleInstruction(
      config.turnPenaltySingleLowHp(),
      config.turnPenaltySingleHighHp()
    );
  }

  private record ActorScope(
    ScopeType scopeType,
    List<String> actors
  ) {
  }

  private enum ScopeType {
    SINGLE,
    PARTY
  }
}
