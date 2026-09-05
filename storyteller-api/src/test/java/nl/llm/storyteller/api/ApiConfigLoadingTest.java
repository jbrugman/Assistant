package nl.llm.storyteller.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiConfigLoadingTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  @DisplayName("""
    Given no local API configuration override,
    When the API module configuration is loaded,
    Then it should use its own bundled defaults relative to the application directory
    """)
  void shouldLoadBundledApiDefaults() {
    ApiConfig apiConfig = ApiConfigLoader.load(temporaryDirectory, null);

    assertEquals("0.0.0.0", apiConfig.host());
    assertEquals(7070, apiConfig.port());
    assertEquals(temporaryDirectory.resolve("memory/storyteller-api"), apiConfig.databasePath());
    assertEquals("sa", apiConfig.databaseUsername());
    assertEquals("", apiConfig.databasePassword());
    assertEquals(Duration.ofMinutes(60), apiConfig.sessionInactivityTimeout());
  }

  @Test
  @DisplayName("""
    Given an application.config for the API module,
    When the API configuration is loaded,
    Then it should override the bundled API defaults
    """)
  void shouldLoadApiOverridesFromApplicationConfig() throws Exception {
    Path configFile = temporaryDirectory.resolve("application.config");
    Files.writeString(configFile, """
      api.host=127.0.0.1
      api.port=8081
      api.database.path=data/storyteller
      api.database.username=storyteller
      api.database.password=secret
      api.sessionTimeoutMinutes=90
      """);

    ApiConfig apiConfig = ApiConfigLoader.load(temporaryDirectory, configFile);

    assertEquals("127.0.0.1", apiConfig.host());
    assertEquals(8081, apiConfig.port());
    assertEquals(temporaryDirectory.resolve("data/storyteller"), apiConfig.databasePath());
    assertEquals("storyteller", apiConfig.databaseUsername());
    assertEquals("secret", apiConfig.databasePassword());
    assertEquals(Duration.ofMinutes(90), apiConfig.sessionInactivityTimeout());
  }
}
