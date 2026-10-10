package nl.llm.storyteller.db;

import nl.llm.storyteller.core.config.MysqlConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in only. Requires CREATE/DROP permission for an isolated temporary database. */
@EnabledIfSystemProperty(named = "mysql.integration", matches = "true")
class MysqlEncryptionIT {
  @Test
  @DisplayName("""
    Given explicit MySQL integration opt-in and a separate temporary database,
    When legacy content is migrated and encrypted repository operations are exercised,
    Then MySQL should preserve behavior without migrating the configured Storyteller database
    """)
  void migratesAndExercisesEncryptedStorageInAnIsolatedDatabase() throws Exception {
    Properties properties = new Properties();
    try (var reader = Files.newBufferedReader(Path.of(System.getProperty("mysql.config")))) {
      properties.load(reader);
    }
    MysqlConfig mysql = MysqlConfig.from(properties);
    assertTrue(mysql.enabled());
    Database administrator = new Database(mysql.url(), mysql.username(), mysql.password());
    String name = "storyteller_encryption_test_" + UUID.randomUUID().toString().replace("-", "");
    java.net.URI uri = java.net.URI.create(mysql.url().substring(5));
    String url = "jdbc:mysql://" + uri.getRawAuthority() + "/" + name
      + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    try (var connection = administrator.openConnection(); var statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + name + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin");
    }
    try {
      Database plain = new Database(url, mysql.username(), mysql.password());
      new SchemaInitializer(plain).initialize();
      Instant now = Instant.parse("2026-10-10T12:00:00Z");
      SessionRecord legacy = new SessionRecord("legacy", "Legacy 🦉", now, now, now, now.plusSeconds(3600), true);
      SessionPrompts prompts = new SessionPrompts("Legacy system", "Legacy protagonists", "Legacy rules");
      new JdbcSessionRepository(plain).create(legacy, prompts);
      Database encrypted = new Database(url, mysql.username(), mysql.password(),
        DatabaseEncryption.fromBase64(DatabaseEncryptionTest.KEY));
      new SchemaInitializer(encrypted).initialize();
      new SchemaInitializer(encrypted).initialize();
      assertEquals(legacy, new JdbcSessionRepository(encrypted).findById("legacy").orElseThrow());
      assertEquals(prompts, new JdbcSessionPromptRepository(encrypted).load("legacy"));
      assertThrows(IllegalStateException.class, () -> new SchemaInitializer(plain).initialize());
      DatabaseEncryptionStorageTest.exerciseRepositories(encrypted);
    } finally {
      try (var connection = administrator.openConnection(); var statement = connection.createStatement()) {
        statement.execute("DROP DATABASE " + name);
      }
    }
  }
}
