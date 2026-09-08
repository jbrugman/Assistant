package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.FileSupport;

import java.nio.file.Path;

public final class FileTextMemory implements TextMemory {
  private final Path path;

  public FileTextMemory(Path path) {
    this.path = path;
  }

  @Override
  public String load() {
    return FileSupport.readTextFile(path);
  }

  @Override
  public void save(String content) {
    FileSupport.writeTextFile(path, content);
  }
}
