package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.FileSupport;
import nl.llm.storyteller.core.TestAppConfigFactory;
import nl.llm.storyteller.core.model.TurnRuleDecision;
import nl.llm.storyteller.core.model.TurnState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnManagerTest {
    @Test
    @DisplayName("""
        Given turn-based mode is enabled for a fixed party,
        When the start trigger is received,
        Then the turn state should be initialized with all protagonists at zero turns in round one
        """)
    void shouldInitializeTurnStateOnStartTrigger() throws Exception {
        TestContext context = createContext(true);

        TurnRuleDecision decision = context.turnManager().evaluate("start");
        TurnState turnState = context.turnStateStore().load();

        assertFalse(decision.hasInstruction());
        assertTrue(turnState.started());
        assertEquals(1, turnState.roundNumber());
        assertEquals(0, turnState.turnsThisRound().get("Eldrin"));
        assertEquals(0, turnState.turnsThisRound().get("Thorin"));
    }

    @Test
    @DisplayName("""
        Given one protagonist already acted in the current round,
        When the same protagonist tries to act again before the others,
        Then a single-character turn violation instruction should be returned
        """)
    void shouldReportSingleCharacterTurnViolation() throws Exception {
        TestContext context = createContext(true);

        context.turnManager().evaluate("start");
        context.turnManager().evaluate("(Eldrin) I open the first door.");
        TurnRuleDecision decision = context.turnManager().evaluate("(Eldrin) I immediately charge ahead again.");
        TurnState turnState = context.turnStateStore().load();

        assertTrue(decision.hasInstruction());
        assertTrue(decision.promptInstruction().contains("this action is attempted out of turn"));
        assertTrue(decision.promptInstruction().contains("must lose 5 or 10 health points"));
        assertEquals(2, turnState.turnsThisRound().get("Eldrin"));
    }

    @Test
    @DisplayName("""
        Given one protagonist already acted in the current round,
        When the party tries to act together before the remaining protagonists have acted,
        Then a party turn violation instruction should be returned with the capped party penalty
        """)
    void shouldReportPartyTurnViolation() throws Exception {
        TestContext context = createContext(true);

        context.turnManager().evaluate("start");
        context.turnManager().evaluate("(Eldrin) I check the stairs first.");
        TurnRuleDecision decision = context.turnManager().evaluate("(Party) We all rush through the doorway.");

        assertTrue(decision.hasInstruction());
        assertTrue(decision.promptInstruction().contains("attempted out of turn by the party"));
        assertTrue(decision.promptInstruction().contains("must lose up to 5 health points"));
    }

    @Test
    @DisplayName("""
        Given turn-based mode is disabled,
        When the same protagonist acts repeatedly,
        Then no turn violation instruction should be produced
        """)
    void shouldDoNothingWhenTurnBasedModeIsDisabled() throws Exception {
        TestContext context = createContext(false);

        context.turnManager().evaluate("start");
        context.turnManager().evaluate("(Eldrin) I move first.");
        TurnRuleDecision decision = context.turnManager().evaluate("(Eldrin) I move again.");

        assertFalse(decision.hasInstruction());
    }

    private TestContext createContext(boolean turnBasedEnabled) throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-turn-manager");
        writeOverride(baseDirectory, "systemprompts/fixed_protagonists.yml", """
            game:
              trigger_word: "start"

            fixed_protagonist:
              - name: "Eldrin"
              - name: "Thorin"
              - name: "Lyra"
            """);
        writeOverride(baseDirectory, "systemprompts/application.config", """
            game.turnBasedModeEnabled=%s
            game.turnPenaltySingleLowHp=5
            game.turnPenaltySingleHighHp=10
            """.formatted(turnBasedEnabled));

        nl.llm.storyteller.core.config.AppConfig config = TestAppConfigFactory.load(baseDirectory);
        PromptResourceLoader promptResourceLoader = new PromptResourceLoader(config);
        TurnStateStore turnStateStore = new TurnStateStore(config.turnStateFile());
        TurnManager turnManager = new TurnManager(
            config,
            promptResourceLoader,
            new PromptTemplateService(promptResourceLoader),
            new GameModeDefinitionParser(),
            turnStateStore
        );
        return new TestContext(turnManager, turnStateStore);
    }

    private void writeOverride(Path baseDirectory, String relativePath, String content) {
        Path path = baseDirectory.resolve(relativePath);
        try {
            Files.createDirectories(path.getParent());
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
        FileSupport.writeTextFile(path, content);
    }

    private record TestContext(
        TurnManager turnManager,
        TurnStateStore turnStateStore
    ) {}
}
