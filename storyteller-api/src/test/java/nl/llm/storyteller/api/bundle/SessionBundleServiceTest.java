package nl.llm.storyteller.api.bundle;

import nl.llm.storyteller.api.persistence.SessionRecord;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.model.HistoryState;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.model.TurnState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SessionBundleServiceTest {
  private static final Instant NOW = Instant.parse("2026-09-06T16:00:00Z");

  @Test
  @DisplayName("""
    Given a complete CLI-compatible session bundle,
    When it is exported and imported,
    Then every memory file and history cursor should survive the round trip
    """)
  void shouldRoundTripSessionBundle() throws Exception {
    SessionBundle original = new SessionBundle(
      new HistoryState(
        List.of(new Message("user", "Open the door"), new Message("assistant", "It opens.")),
        2,
        1,
        2
      ),
      "Long-term summary",
      "Recent summary",
      "currentLocation: library",
      TurnState.inactive(),
      KnowledgeGraphDocument.empty()
    );
    RecordingRepository repository = new RecordingRepository(original);
    SessionBundleService service = service(repository);

    byte[] archive = service.exportArchive("existing-session", "The Library");
    SessionRecord imported = service.importArchive(
      new ByteArrayInputStream(archive), archive.length, "The Library.zip"
    );

    assertEquals("The Library", imported.title());
    assertEquals("imported-session", imported.sessionId());
    assertFalse(imported.infinite());
    assertEquals(original, repository.importedBundle);
  }

  @Test
  @DisplayName("""
    Given a CLI session ZIP created by macOS Finder,
    When the session is imported,
    Then Finder and AppleDouble metadata should be ignored
    """)
  void shouldIgnoreMacOsMetadata() throws Exception {
    RecordingRepository repository = new RecordingRepository(null);
    SessionBundleService service = service(repository);
    byte[] archive = archiveWithMacOsMetadata();

    service.importArchive(new ByteArrayInputStream(archive), archive.length, "Story.zip");

    assertEquals(2, repository.importedBundle.history().messages().size());
  }

  private byte[] archiveWithMacOsMetadata() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
      writeEntry(zip, "history.json", """
        {"messages":[
          {"role":"user","content":"Begin"},
          {"role":"assistant","content":"Started"}
        ]}
        """);
      writeEntry(zip, "__MACOSX/._history.json", "finder metadata");
      writeEntry(zip, ".DS_Store", "finder metadata");
    }
    return output.toByteArray();
  }

  private void writeEntry(ZipOutputStream zip, String name, String content) throws Exception {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  private SessionBundleService service(SessionBundleRepository repository) {
    return new SessionBundleService(
      repository,
      Duration.ofHours(1),
      Clock.fixed(NOW, ZoneOffset.UTC),
      () -> "imported-session"
    );
  }

  private static final class RecordingRepository implements SessionBundleRepository {
    private final SessionBundle exportedBundle;
    private SessionBundle importedBundle;

    private RecordingRepository(SessionBundle exportedBundle) {
      this.exportedBundle = exportedBundle;
    }

    @Override
    public SessionBundle load(String sessionId) {
      return exportedBundle;
    }

    @Override
    public void create(SessionRecord session, SessionBundle bundle) {
      importedBundle = bundle;
    }
  }
}
