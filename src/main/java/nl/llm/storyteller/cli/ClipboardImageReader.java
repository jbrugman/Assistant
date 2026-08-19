package nl.llm.storyteller.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;

final class ClipboardImageReader {
  String readPngDataUrl() throws IOException {
    Path image = Files.createTempFile("storyteller-clipboard-", ".png");
    try {
      Process process = new ProcessBuilder(command(image)).redirectErrorStream(true).start();
      String output = new String(process.getInputStream().readAllBytes());
      try {
        if (process.waitFor() != 0 || Files.size(image) == 0) {
          throw new IllegalArgumentException(output.isBlank() ? "The clipboard does not contain an image." : output.trim());
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while reading the clipboard image.", ex);
      }
      return "data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(image));
    } finally {
      Files.deleteIfExists(image);
    }
  }

  private String[] command(Path image) {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("mac")) {
      String script = "set imageData to the clipboard as «class PNGf»\n"
        + "set outputFile to open for access POSIX file \"" + image + "\" with write permission\n"
        + "set eof outputFile to 0\nwrite imageData to outputFile\nclose access outputFile";
      return new String[]{"osascript", "-e", script};
    }
    if (os.contains("win")) {
      String path = image.toString().replace("'", "''");
      String script = "Add-Type -AssemblyName System.Windows.Forms; Add-Type -AssemblyName System.Drawing; "
        + "$image=[System.Windows.Forms.Clipboard]::GetImage(); if($null -eq $image){exit 2}; "
        + "$image.Save('" + path + "',[System.Drawing.Imaging.ImageFormat]::Png); $image.Dispose()";
      return new String[]{"powershell.exe", "-STA", "-NoProfile", "-NonInteractive", "-Command", script};
    }
    throw new IllegalArgumentException("Clipboard images are supported only on macOS and Windows.");
  }
}
