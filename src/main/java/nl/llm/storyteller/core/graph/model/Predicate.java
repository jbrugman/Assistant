package nl.llm.storyteller.core.graph.model;

public enum Predicate {
  POSSESSES(EntityType.CHARACTER, EntityType.ITEM, true),
  CAN_PERFORM(EntityType.CHARACTER, EntityType.SKILL, false),
  LOVES(EntityType.CHARACTER, EntityType.CHARACTER, false);

  private final EntityType subjectType;
  private final EntityType objectType;
  private final boolean temporal;

  Predicate(EntityType subjectType, EntityType objectType, boolean temporal) {
    this.subjectType = subjectType;
    this.objectType = objectType;
    this.temporal = temporal;
  }

  public EntityType subjectType() {
    return subjectType;
  }

  public EntityType objectType() {
    return objectType;
  }

  public boolean temporal() {
    return temporal;
  }
}
