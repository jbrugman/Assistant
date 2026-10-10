package nl.llm.storyteller.cli;

import nl.llm.storyteller.db.StoryImage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;

final class ClipboardImageReader {
  private final ProcessStarter processStarter;

  ClipboardImageReader() {
    this(image -> new ProcessBuilder(command(image)).redirectErrorStream(true).start());
  }

  ClipboardImageReader(ProcessStarter processStarter) {
    this.processStarter = processStarter;
  }

  String readPngDataUrl() throws IOException {
    Path image = Files.createTempFile("storyteller-clipboard-", ".png");
    try {
      Process process = processStarter.start(image);
      String output = new String(process.getInputStream().readAllBytes());
      try {
        if (process.waitFor() != 0 || Files.size(image) == 0) {
          throw new IllegalArgumentException(output.isBlank() ? "The clipboard does not contain an image." : output.trim());
        }
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while reading the clipboard image.", ex);
      }
      if (Files.size(image) > StoryImage.MAX_UPLOAD_BYTES) {
        throw new IllegalArgumentException("The pasted image must not exceed 5 MiB.");
      }
      return "data:image/png;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(image));
    } finally {
      Files.deleteIfExists(image);
    }
  }

  private static String[] command(Path image) {
    return command(System.getProperty("os.name", ""), image);
  }

  static String[] command(String osName, Path image) {
    String os = osName.toLowerCase(Locale.ROOT);
    if (os.contains("mac")) {
      String script = "set imageData to the clipboard as «class PNGf»\n"
        + "set outputFile to open for access POSIX file \"" + image + "\" with write permission\n"
        + "set eof outputFile to 0\nwrite imageData to outputFile\nclose access outputFile";
      return new String[]{"/bin/sh", "-c",
        "osascript -e \"$1\" && width=$(sips -g pixelWidth \"$2\" | awk '/pixelWidth:/ {print $2}')"
          + " && if [ \"$width\" -gt 1024 ]; then sips --resampleWidth 1024 \"$2\" >/dev/null; fi"
          + " && height=$(sips -g pixelHeight \"$2\" | awk '/pixelHeight:/ {print $2}')"
          + " && if [ \"$height\" -gt 768 ]; then sips --resampleHeight 768 \"$2\" >/dev/null; fi",
        "clipboard-image", script, image.toString()};
    }
    if (os.contains("win")) {
      String path = image.toString().replace("'", "''");
      String script = "Add-Type -AssemblyName System.Windows.Forms; Add-Type -AssemblyName System.Drawing; "
        + "$image=[System.Windows.Forms.Clipboard]::GetImage(); if($null -eq $image){exit 2}; "
        + "try { $scale=[Math]::Min(1.0,[Math]::Min(1024.0/$image.Width,768.0/$image.Height)); "
        + "$width=[Math]::Max(1,[int][Math]::Round($image.Width*$scale)); "
        + "$height=[Math]::Max(1,[int][Math]::Round($image.Height*$scale)); "
        + "$resized=New-Object System.Drawing.Bitmap($width,$height); "
        + "try { $graphics=[System.Drawing.Graphics]::FromImage($resized); "
        + "try { $graphics.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic; "
        + "$graphics.DrawImage($image,0,0,$width,$height); } finally { $graphics.Dispose(); } "
        + "$resized.Save('" + path + "',[System.Drawing.Imaging.ImageFormat]::Png); "
        + "} finally { $resized.Dispose(); } } finally { $image.Dispose(); }";
      return new String[]{"powershell.exe", "-STA", "-NoProfile", "-NonInteractive", "-Command", script};
    }
    throw new IllegalArgumentException("Clipboard images are supported only on macOS and Windows.");
  }

  @FunctionalInterface
  interface ProcessStarter {
    Process start(Path image) throws IOException;
  }
}
