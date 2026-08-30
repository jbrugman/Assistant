package nl.llm.storyteller.core.graph.model;

import java.util.List;

public record Entity(
  EntityType type,
  String name,
  List<String> aliases,
  FactSource source
) {
  public Entity(EntityType type, String name, List<String> aliases) {
    this(type, name, aliases, FactSource.MANUAL);
  }

  public Entity {
    name = name == null ? "" : name.trim();
    aliases = aliases == null
      ? List.of()
      : aliases.stream().map(Entity::normalizeAlias).toList();
  }

  private static String normalizeAlias(String alias) {
    return alias == null ? "" : alias.trim();
  }
}
