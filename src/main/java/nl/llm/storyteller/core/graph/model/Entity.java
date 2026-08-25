package nl.llm.storyteller.core.graph.model;

import java.util.List;

public record Entity(
  EntityType type,
  String name,
  List<String> aliases
) {
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
