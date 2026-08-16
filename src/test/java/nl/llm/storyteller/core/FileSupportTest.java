package nl.llm.storyteller.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSupportTest {
    @Test
    @DisplayName("""
        Given a target file inside a missing parent directory,
        When text is written through the file support helper,
        Then the parent directories should be created automatically
        """)
    void shouldCreateParentDirectoriesWhenWritingTextFiles() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-write-file");
        Path targetFile = baseDirectory.resolve("memory/nested/output.txt");

        FileSupport.writeTextFile(targetFile, "hello");

        assertTrue(Files.exists(targetFile));
        assertEquals("hello", Files.readString(targetFile).trim());
    }

    @Test
    @DisplayName("""
        Given no local override file for a required prompt resource,
        When the text is loaded through the resource-aware file support helper,
        Then the bundled classpath resource should be returned
        """)
    void shouldLoadBundledResourceWhenNoLocalOverrideExists() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-resource-base");
        Path requestedPath = baseDirectory.resolve("systemprompts/systemprompt.md");

        String content = FileSupport.readRequiredTextFileOrResource(requestedPath, baseDirectory);

        assertTrue(content.contains("You are an English-language story writing assistant"));
        assertTrue(content.contains("You are not the author of the story."));
        assertTrue(content.contains("You may not:"));
    }

    @Test
    @DisplayName("""
        Given a local override file with the same logical prompt path as a bundled default,
        When the text is loaded through the resource-aware file support helper,
        Then the local override file should take precedence over the bundled resource
        """)
    void shouldPreferLocalOverrideOverBundledResource() throws Exception {
        Path baseDirectory = Files.createTempDirectory("storyteller-resource-override");
        Path requestedPath = baseDirectory.resolve("systemprompts/systemprompt.md");
        Files.createDirectories(requestedPath.getParent());
        Files.writeString(requestedPath, "Local storyteller override");

        String content = FileSupport.readRequiredTextFileOrResource(requestedPath, baseDirectory);

        assertEquals("Local storyteller override", content);
    }
}
