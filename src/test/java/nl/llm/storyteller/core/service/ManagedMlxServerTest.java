package nl.llm.storyteller.core.service;

import nl.llm.storyteller.core.config.MlxServerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManagedMlxServerTest {
  @Test
  @DisplayName("""
    Given a local MLX model and mlx-vlm arguments,
    When the managed server command is built,
    Then it should bind only to loopback and include the configured model, port, and arguments
    """)
  void shouldBuildLoopbackMlxServerCommand() {
    nl.llm.storyteller.core.config.MlxServerConfig config = new nl.llm.storyteller.core.config.MlxServerConfig(
      "python3", Path.of("/models/story-mlx"), 0, 180,
      "-m mlx_vlm.server --max-kv-size 32768 --max-tokens 32768"
    );

    List<String> command = ManagedMlxServer.buildCommand(config, 8080);

    assertEquals(List.of(
      "python3", "-m", "mlx_vlm.server", "--max-kv-size", "32768", "--max-tokens", "32768",
      "--model", "/models/story-mlx",
      "--host", "127.0.0.1", "--port", "8080"
    ), command);
  }
}
