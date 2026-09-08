package nl.llm.storyteller.api.bundle;

import nl.llm.storyteller.api.persistence.SessionRecord;
import nl.llm.storyteller.api.persistence.SessionPrompts;
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
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionBundleServiceTest {
  private static final Instant NOW = Instant.parse("2026-09-06T16:00:00Z");
  private static final String PNG_DATA_URL = "data:image/png;base64,iVBORw0KGgo=";

  @Test
  @DisplayName("""
    Given a complete CLI-compatible session bundle,
    When it is exported and imported,
    Then every memory file and history cursor should survive the round trip
    """)
  void shouldRoundTripSessionBundle() throws Exception {
    SessionBundle original = new SessionBundle(
      new HistoryState(
        List.of(
          Message.withImage("user", "Open the door", PNG_DATA_URL),
          new Message("assistant", "It opens.")
        ),
        2,
        1,
        2
      ),
      "Long-term summary",
      "Recent summary",
      "currentLocation: library",
      TurnState.inactive(),
      KnowledgeGraphDocument.empty(),
      new SessionPrompts("Story system", "fixed_protagonists: []", "Story rules")
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
    assertTrue(archiveContains(archive, "memory/images/000.png"));
  }

  private boolean archiveContains(byte[] archive, String expectedName) throws Exception {
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (expectedName.equals(entry.getName())) {
          return true;
        }
      }
    }
    return false;
  }

  @Test
  @DisplayName("""
    Given an older CLI session ZIP without prompt files and with macOS Finder metadata,
    When the session is imported,
    Then metadata should be ignored and missing prompts should use the current defaults
    """)
  void shouldImportLegacyBundleUsingDefaultPrompts() throws Exception {
    RecordingRepository repository = new RecordingRepository(null);
    SessionBundleService service = service(repository);
    byte[] archive = archiveWithMacOsMetadata();

    service.importArchive(new ByteArrayInputStream(archive), archive.length, "Story.zip");

    assertEquals(2, repository.importedBundle.history().messages().size());
    assertEquals(defaultPrompts(), repository.importedBundle.prompts());
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
      () -> "imported-session",
      defaultPrompts()
    );
  }

  private SessionPrompts defaultPrompts() {
    return new SessionPrompts("Default system", "fixed_protagonists: []", "Default rules");
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
