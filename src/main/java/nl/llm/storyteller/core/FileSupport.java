package nl.llm.storyteller.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileSupport {
  private FileSupport() {
  }

  public static String readTextFile(Path path) {
    if (!Files.exists(path)) {
      return "";
    }

    try {
      return Files.readString(path, StandardCharsets.UTF_8).trim();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  public static String readRequiredTextFile(Path path) {
    if (!Files.exists(path)) {
      throw new IllegalStateException("Missing required text file: " + path);
    }

    try {
      return Files.readString(path, StandardCharsets.UTF_8).trim();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  public static String readRequiredTextFileOrResource(Path path, Path baseDir) {
    if (Files.exists(path)) {
      return readRequiredTextFile(path);
    }

    if (path.isAbsolute() && !path.startsWith(baseDir)) {
      throw new IllegalStateException("Missing required text file: " + path);
    }

    Path relativePath = path.isAbsolute() ? baseDir.relativize(path) : path;
    String resourceName = relativePath.toString().replace('\\', '/');

    try (InputStream input = FileSupport.class.getClassLoader().getResourceAsStream(resourceName)) {
      if (input == null) {
        throw new IllegalStateException("Missing required text resource: " + resourceName);
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  public static void writeTextFile(Path path, String content) {
    String normalized = content == null ? "" : content.trim();
    String output = normalized.isEmpty() ? "" : normalized + System.lineSeparator();

    try {
      Path parent = path.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(path, output, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
