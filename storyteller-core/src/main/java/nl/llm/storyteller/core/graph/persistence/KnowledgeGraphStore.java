package nl.llm.storyteller.core.graph.persistence;

import nl.llm.storyteller.core.AtomicFileWriter;
import nl.llm.storyteller.core.JsonSupport;
import nl.llm.storyteller.core.graph.KnowledgeGraphSnapshot;
import nl.llm.storyteller.core.graph.KnowledgeGraphValidator;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;
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

    try {
      AtomicFileWriter.write(path, JsonSupport.OBJECT_MAPPER.writeValueAsBytes(codec.toJson(document)));
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  public synchronized KnowledgeGraphDocument update(UnaryOperator<KnowledgeGraphDocument> update) {
    KnowledgeGraphDocument updated = update.apply(load());
    save(updated);
    return updated;
  }

}
