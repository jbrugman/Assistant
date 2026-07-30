package nl.llm.storyteller.model;

public record TurnRuleDecision(
    String promptInstruction
) {
    public static TurnRuleDecision none() {
        return new TurnRuleDecision("");
    }

    public boolean hasInstruction() {
        return promptInstruction != null && !promptInstruction.isBlank();
    }
}
