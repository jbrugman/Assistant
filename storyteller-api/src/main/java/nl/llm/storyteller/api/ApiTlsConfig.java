package nl.llm.storyteller.api;

import java.nio.file.Path;
import java.util.List;

public record ApiTlsConfig(
  boolean enabled,
  int port,
  Path directory,
  List<String> subjectAlternativeNames
) {
  static ApiTlsConfig disabled(Path directory) {
    return new ApiTlsConfig(false, 7443, directory, List.of());
  }

  public ApiTlsConfig {
    if (port < 1 || port > 65_535) {
      throw new IllegalArgumentException("API TLS port must be between 1 and 65535.");
    }
    if (directory == null) {
      throw new IllegalArgumentException("API TLS directory must not be null.");
    }
    subjectAlternativeNames = subjectAlternativeNames == null
      ? List.of()
      : subjectAlternativeNames.stream().map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
  }
}
