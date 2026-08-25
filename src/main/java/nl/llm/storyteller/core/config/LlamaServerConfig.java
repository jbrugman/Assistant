package nl.llm.storyteller.core.config;

import java.nio.file.Path;

public record LlamaServerConfig(
  String command,
  Path modelPath,
  int port,
  int startupTimeoutSeconds,
  String arguments
) { }
