package nl.llm.storyteller.api.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaInitializerTest {
  private static final Set<String> EXPECTED_TABLES = Set.of(
    "story_session",
    "session_configuration",
    "session_prompt_override",
    "story_message",
    "session_memory",
    "turn_state",
    "turn_protagonist",
    "knowledge_graph",
    "knowledge_entity",
    "knowledge_entity_alias",
    "knowledge_fact"
  );

  @TempDir
  Path temporaryDirectory;

  @Test
  @DisplayName("""
    Given an empty API database,
    When the schema is initialized more than once,
    Then it should contain the complete schema without failing
    """)
  void shouldInitializeCompleteSchemaIdempotently() throws Exception {
    Database database = database();
    SchemaInitializer initializer = new SchemaInitializer(database);

    initializer.initialize();
    initializer.initialize();

    assertEquals(EXPECTED_TABLES, storytellerTables(database));
  }

  private Database database() {
    return new Database("jdbc:h2:file:" + temporaryDirectory.resolve("schema"), "sa", "");
  }

  private Set<String> storytellerTables(Database database) throws Exception {
    Set<String> tables = new HashSet<>();
    try (Connection connection = database.openConnection();
         ResultSet resultSet = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
      while (resultSet.next()) {
        String table = resultSet.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
        if (EXPECTED_TABLES.contains(table)) {
          tables.add(table);
        }
      }
    }
    return tables;
  }
}
