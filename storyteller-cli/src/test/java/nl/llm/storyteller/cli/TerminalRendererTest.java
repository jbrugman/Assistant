package nl.llm.storyteller.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerminalRendererTest {
    @Test
    @DisplayName("""
        Given a display line wider than the available content width,
        When the terminal renderer formats it,
        Then it should wrap words with the configured display margin
        """)
    void shouldWrapDisplayTextAtTheAvailableContentWidth() {
        String formatted = TerminalRenderer.formatForDisplay("one two three", 8);

        assertEquals("  one two\n  three", formatted);
    }

    @Test
    @DisplayName("""
        Given text containing a fenced code block,
        When the terminal renderer formats it,
        Then it should leave the code block unchanged while wrapping surrounding prose
        """)
    void shouldPreserveFencedCodeBlocks() {
        String formatted = TerminalRenderer.formatForDisplay("intro words\n```\n  code stays\n```", 8);

        assertEquals("  intro\n  words\n```\n  code stays\n```", formatted);
    }
}
