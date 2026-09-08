package nl.llm.storyteller.core.service;

public interface TextMemory {
  String load();

  void save(String content);
}
