package nl.llm.storyteller.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtomicFileWriterTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  @DisplayName("""
    Given an existing persistent file,
    When writing its replacement fails after creating partial output,
    Then the original file should remain intact and temporary output should be removed
    """)
  void shouldPreserveOriginalFileWhenReplacementFails() throws Exception {
    Path target = temporaryDirectory.resolve("history.json");
    Files.writeString(target, "original", StandardCharsets.UTF_8);

    assertThrows(UncheckedIOException.class, () -> AtomicFileWriter.write(target, temporaryPath -> {
      Files.writeString(temporaryPath, "partial", StandardCharsets.UTF_8);
      throw new IOException("disk full");
    }));

    assertEquals("original", Files.readString(target, StandardCharsets.UTF_8));
    try (var files = Files.list(temporaryDirectory)) {
      assertEquals(1, files.count());
    }
  }

  @Test
  @DisplayName("""
    Given a missing parent directory,
    When a file is written safely,
    Then its directories and complete content should be created
    """)
  void shouldCreateDirectoriesAndWriteCompleteContent() throws Exception {
    Path target = temporaryDirectory.resolve("memory/history.json");

    AtomicFileWriter.write(target, "complete".getBytes(StandardCharsets.UTF_8));

    assertEquals("complete", Files.readString(target, StandardCharsets.UTF_8));
  }
}
