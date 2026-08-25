package nl.llm.storyteller.core.graph.persistence;

import nl.llm.storyteller.core.JsonSupport;
import nl.llm.storyteller.core.graph.KnowledgeGraphSnapshot;
import nl.llm.storyteller.core.graph.KnowledgeGraphValidator;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class KnowledgeGraphStore {
  private final Path path;
  private final KnowledgeGraphValidator validator;
  private final KnowledgeGraphJsonCodec codec = new KnowledgeGraphJsonCodec();

  public KnowledgeGraphStore(Path path) {
    this(path, new KnowledgeGraphValidator());
  }

  public KnowledgeGraphStore(Path path, KnowledgeGraphValidator validator) {
    this.path = Objects.requireNonNull(path, "path");
    this.validator = Objects.requireNonNull(validator, "validator");
  }

  public synchronized KnowledgeGraphDocument load() {
    if (!Files.exists(path)) {
      return KnowledgeGraphDocument.empty();
    }

    try {
      KnowledgeGraphDocument document = codec.fromJson(Files.readString(path));
      validator.validate(document);
      return document;
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalArgumentException("Invalid JSON in " + path + ": " + ex.getOriginalMessage(), ex);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  public synchronized KnowledgeGraphSnapshot loadSnapshot() {
    return KnowledgeGraphSnapshot.from(load(), validator);
  }

  public synchronized void save(KnowledgeGraphDocument document) {
    validator.validate(document);

    Path absolutePath = path.toAbsolutePath();
    Path parent = absolutePath.getParent();
    Path temporaryPath = null;
    try {
      if (parent != null) {
        Files.createDirectories(parent);
      }
      String fileName = absolutePath.getFileName().toString();
      String temporaryPrefix = fileName.length() >= 3 ? fileName : (fileName + "___").substring(0, 3);
      temporaryPath = Files.createTempFile(parent, temporaryPrefix, ".tmp");
      JsonSupport.OBJECT_MAPPER.writeValue(temporaryPath.toFile(), codec.toJson(document));
      moveAtomicallyWhereSupported(temporaryPath, absolutePath);
      temporaryPath = null;
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    } finally {
      deleteTemporaryFile(temporaryPath);
    }
  }

  private void moveAtomicallyWhereSupported(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException _) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void deleteTemporaryFile(Path temporaryPath) {
    if (temporaryPath == null) {
      return;
    }
    try {
      Files.deleteIfExists(temporaryPath);
    } catch (IOException _) {
      // Best-effort cleanup after the original write failure.
    }
  }
}
