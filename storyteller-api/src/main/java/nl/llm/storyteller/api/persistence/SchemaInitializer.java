package nl.llm.storyteller.api.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class SchemaInitializer {
  private static final String SCHEMA_RESOURCE = "/db/schema.sql";
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

  private final Database database;

  public SchemaInitializer(Database database) {
    this.database = database;
  }

  public void initialize() {
    try (Connection connection = database.openConnection()) {
      Set<String> existingTables = existingTables(connection);
      Set<String> existingSchemaTables = new HashSet<>(existingTables);
      existingSchemaTables.retainAll(EXPECTED_TABLES);
      if (existingSchemaTables.equals(EXPECTED_TABLES)) {
        return;
      }
      if (!existingSchemaTables.isEmpty()) {
        throw new IllegalStateException("Database contains an incomplete Storyteller schema.");
      }
      executeSchema(connection, loadSchema());
    } catch (SQLException ex) {
      throw new DatabaseException("Could not initialize the API database schema.", ex);
    }
  }

  private Set<String> existingTables(Connection connection) throws SQLException {
    Set<String> tables = new HashSet<>();
    try (ResultSet resultSet = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
      while (resultSet.next()) {
        tables.add(resultSet.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
      }
    }
    return tables;
  }

  private void executeSchema(Connection connection, String schema) throws SQLException {
    for (String statement : Arrays.stream(schema.split(";"))
      .map(String::trim)
      .filter(value -> !value.isEmpty())
      .toList()) {
      try (var sql = connection.createStatement()) {
        sql.execute(statement);
      }
    }
  }

  private String loadSchema() {
    try (var input = SchemaInitializer.class.getResourceAsStream(SCHEMA_RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException("Missing database schema resource: " + SCHEMA_RESOURCE);
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not read database schema resource.", ex);
    }
  }
}
