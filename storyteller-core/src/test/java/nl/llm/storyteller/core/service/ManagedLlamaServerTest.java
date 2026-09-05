package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.config.LlamaServerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManagedLlamaServerTest {
  @Test
  @DisplayName("""
    Given a local GGUF model and llama-server arguments,
    When the managed server command is built,
    Then it should bind only to loopback and include the configured model, port, and arguments
    """)
  void shouldBuildLoopbackLlamaServerCommand() {
    nl.llm.storyteller.core.config.LlamaServerConfig config = new nl.llm.storyteller.core.config.LlamaServerConfig(
      "llama-server", Path.of("/models/story.gguf"), 0, 120, "--ctx-size 32768 --n-gpu-layers 99"
    );

    List<String> command = ManagedLlamaServer.buildCommand(config, 8080);

    assertEquals(List.of(
      "llama-server", "--ctx-size", "32768", "--n-gpu-layers", "99", "--model", "/models/story.gguf",
      "--host", "127.0.0.1", "--port", "8080"
    ), command);
  }
}
