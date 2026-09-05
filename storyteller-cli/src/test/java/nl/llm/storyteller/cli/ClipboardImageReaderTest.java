package nl.llm.storyteller.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipboardImageReaderTest {
  @TempDir
  Path tempDir;

  @Test
  @DisplayName("""
    Given a clipboard process that writes PNG bytes,
    When the clipboard image is read,
    Then a PNG data URL should be returned and the temporary file should be deleted
    """)
  void returnsDataUrlAndDeletesTemporaryFile() throws Exception {
    AtomicReference<Path> temporaryFile = new AtomicReference<>();
    ClipboardImageReader reader = new ClipboardImageReader(image -> {
      temporaryFile.set(image);
      return shellProcess("printf 'PNG' > \"$1\"", image);
    });

    String dataUrl = reader.readPngDataUrl();

    assertEquals("data:image/png;base64,UE5H", dataUrl);
    assertFalse(Files.exists(temporaryFile.get()));
  }

  @Test
  @DisplayName("""
    Given a clipboard process that reports an error,
    When the clipboard image is read,
    Then the process output should be exposed and the temporary file should be deleted
    """)
  void reportsProcessOutputAndDeletesTemporaryFile() {
    AtomicReference<Path> temporaryFile = new AtomicReference<>();
    ClipboardImageReader reader = new ClipboardImageReader(image -> {
      temporaryFile.set(image);
      return shellProcess("printf 'clipboard failed'; exit 2", image);
    });

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, reader::readPngDataUrl);

    assertEquals("clipboard failed", exception.getMessage());
    assertFalse(Files.exists(temporaryFile.get()));
  }

  @Test
  @DisplayName("""
    Given a successful clipboard process that writes no image bytes,
    When the clipboard image is read,
    Then a missing-image error should be reported
    """)
  void reportsMissingImageWhenClipboardProcessWritesNoBytes() {
    ClipboardImageReader reader = new ClipboardImageReader(image -> shellProcess("exit 0", image));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, reader::readPngDataUrl);

    assertEquals("The clipboard does not contain an image.", exception.getMessage());
  }

  @Test
  @DisplayName("""
    Given macOS as the operating system,
    When the clipboard command is selected,
    Then an osascript command writing PNG data to the requested path should be returned
    """)
  void buildsMacOsClipboardCommand() {
    Path image = tempDir.resolve("clipboard.png");

    String[] command = ClipboardImageReader.command("Mac OS X", image);

    assertEquals("osascript", command[0]);
    assertEquals("-e", command[1]);
    assertTrue(command[2].contains(image.toString()));
    assertTrue(command[2].contains("class PNGf"));
  }

  @Test
  @DisplayName("""
    Given Windows as the operating system and a path containing an apostrophe,
    When the clipboard command is selected,
    Then a PowerShell command with an escaped output path should be returned
    """)
  void buildsWindowsClipboardCommandWithEscapedPath() {
    Path image = tempDir.resolve("clip'board.png");

    String[] command = ClipboardImageReader.command("Windows 11", image);

    assertArrayEquals(
      new String[]{"powershell.exe", "-STA", "-NoProfile", "-NonInteractive", "-Command"},
      java.util.Arrays.copyOf(command, 5)
    );
    assertTrue(command[5].contains("clip''board.png"));
  }

  @Test
  @DisplayName("""
    Given an unsupported operating system,
    When the clipboard command is selected,
    Then a clear platform-support error should be reported
    """)
  void rejectsUnsupportedOperatingSystem() {
    IllegalArgumentException exception = assertThrows(
      IllegalArgumentException.class,
      () -> ClipboardImageReader.command("Linux", tempDir.resolve("clipboard.png"))
    );

    assertEquals("Clipboard images are supported only on macOS and Windows.", exception.getMessage());
  }

  private Process shellProcess(String script, Path image) throws java.io.IOException {
    return new ProcessBuilder("/bin/sh", "-c", script, "clipboard-test", image.toString())
      .redirectErrorStream(true)
      .start();
  }
}
