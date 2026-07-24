package nl.llm.storyteller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class FileSupport {
    private FileSupport() {
    }

    static String readTextFile(Path path, String defaultValue) {
        if (!Files.exists(path)) {
            return defaultValue;
        }

        try {
            return Files.readString(path, StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static String readRequiredTextFile(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalStateException("Missing required text file: " + path);
        }

        try {
            return Files.readString(path, StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static void writeTextFile(Path path, String content) {
        String normalized = content == null ? "" : content.trim();
        String output = normalized.isEmpty() ? "" : normalized + System.lineSeparator();

        try {
            Files.writeString(path, output, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
