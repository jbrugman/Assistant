package nl.llm.storyteller.core;

import java.nio.file.Path;

public record MlxServerConfig(
  String command,
  Path modelPath,
  int port,
  int startupTimeoutSeconds,
  String arguments
) { }
