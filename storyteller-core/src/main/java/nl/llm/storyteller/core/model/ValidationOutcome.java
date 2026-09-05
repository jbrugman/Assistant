package nl.llm.storyteller.core.model;

public record ValidationOutcome(
  String decision,
  String replacementText
) {
  public boolean isAllow() {
    return "ALLOW".equals(decision);
  }

  public boolean isReplace() {
    return "REPLACE".equals(decision);
  }
}
