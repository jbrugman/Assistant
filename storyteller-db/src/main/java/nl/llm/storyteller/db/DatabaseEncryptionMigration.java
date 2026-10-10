package nl.llm.storyteller.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** One-time conversion of existing database values, without changing source files. */
final class DatabaseEncryptionMigration {
  private static final String TABLE = "storyteller_database_encryption";
  private static final String CHECK_VALUE = "Storyteller database encryption v1";
  private static final String CHECK_FIELD = "database.key_check";
  private static final List<EncryptedTable> CONTENT_TABLES = List.of(
    new EncryptedTable("story_session", List.of("session_id"), List.of("title")),
    new EncryptedTable("session_prompt_override", List.of("session_id", "override_name"), List.of("override_content")),
    new EncryptedTable("story_message", List.of("session_id", "message_index"), List.of("content", "image_content")),
    new EncryptedTable("session_memory", List.of("session_id"),
      List.of("summary_content", "recent_summary_content", "canonical_state_content")),
    new EncryptedTable("turn_state", List.of("session_id"), List.of("trigger_word")),
    new EncryptedTable("turn_protagonist", List.of("session_id", "protagonist_index"), List.of("protagonist_name")),
    new EncryptedTable("knowledge_entity", List.of("session_id", "entity_id"), List.of("entity_name")),
    new EncryptedTable("knowledge_entity_alias", List.of("session_id", "entity_id", "alias_index"), List.of("alias_name"))
  );

  private DatabaseEncryptionMigration() { }

  static void initialize(Database database, Connection connection, Set<String> existingTables) throws SQLException {
    if (existingTables.contains(TABLE) && verify(database.encryption(), connection)) {
      return;
    }
    if (!database.encryption().enabled()) {
      return;
    }
    expandColumns(database, connection);
    try (var statement = connection.createStatement()) {
      statement.execute("""
        CREATE TABLE IF NOT EXISTS storyteller_database_encryption (
          id INTEGER PRIMARY KEY, version INTEGER NOT NULL, key_check VARCHAR(256) NOT NULL,
          CHECK (id = 1)
        )
        """);
    }
    connection.setAutoCommit(false);
    try {
      for (EncryptedTable table : CONTENT_TABLES) {
        migrateTable(database.encryption(), connection, table);
      }
      try (var statement = connection.prepareStatement(
        "INSERT INTO storyteller_database_encryption (id, version, key_check) VALUES (1, 1, ?)")) {
        statement.setString(1, database.encryption().encrypt("", CHECK_FIELD, CHECK_VALUE));
        statement.executeUpdate();
      }
      connection.commit();
    } catch (SQLException | RuntimeException ex) {
      try {
        connection.rollback();
      } catch (SQLException rollbackFailure) {
        ex.addSuppressed(rollbackFailure);
      }
      throw ex;
    }
  }

  private static boolean verify(DatabaseEncryption encryption, Connection connection) throws SQLException {
    try (var statement = connection.createStatement();
         var result = statement.executeQuery("SELECT version, key_check FROM storyteller_database_encryption WHERE id = 1")) {
      if (!result.next()) {
        return false;
      }
      if (!encryption.enabled()) {
        throw new IllegalStateException("Database is encrypted; configure database.encryption.key before opening it.");
      }
      if (result.getInt("version") != 1
        || !CHECK_VALUE.equals(encryption.decrypt("", CHECK_FIELD, result.getString("key_check")))) {
        throw new IllegalStateException("Unsupported database encryption version or incorrect key.");
      }
      return true;
    }
  }

  static void expandColumns(Database database, Connection connection) throws SQLException {
    for (EncryptedTable table : CONTENT_TABLES) {
      for (String column : table.columns()) {
        if (column.equals("image_content")) {
          continue;
        }
        String tableName = connection.getMetaData().storesUpperCaseIdentifiers()
          ? table.name().toUpperCase(Locale.ROOT) : table.name();
        String columnName = connection.getMetaData().storesUpperCaseIdentifiers()
          ? column.toUpperCase(Locale.ROOT) : column;
        boolean nullable;
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, columnName)) {
          if (!columns.next()) {
            throw new SQLException("Missing database content column: " + table.name() + "." + column);
          }
          nullable = columns.getInt("NULLABLE") != java.sql.DatabaseMetaData.columnNoNulls;
        }
        boolean mysql = database.url().startsWith("jdbc:mysql:");
        String alteration = mysql ? " MODIFY COLUMN " : " ALTER COLUMN ";
        String type = mysql ? " LONGTEXT" : " CHARACTER LARGE OBJECT";
        try (var statement = connection.createStatement()) {
          statement.execute("ALTER TABLE " + table.name() + alteration + column + type
            + (nullable ? " NULL" : " NOT NULL"));
        }
      }
    }
  }

  private static void migrateTable(DatabaseEncryption encryption, Connection connection, EncryptedTable table)
    throws SQLException {
    List<String> selected = new ArrayList<>(table.keys());
    selected.addAll(table.columns());
    String assignments = String.join(", ", table.columns().stream().map(column -> column + " = ?").toList());
    String condition = String.join(" AND ", table.keys().stream().map(column -> column + " = ?").toList());
    try (var select = connection.createStatement();
         var result = select.executeQuery("SELECT " + String.join(", ", selected) + " FROM " + table.name());
         PreparedStatement update = connection.prepareStatement("UPDATE " + table.name() + " SET " + assignments
           + " WHERE " + condition)) {
      while (result.next()) {
        String sessionId = result.getString("session_id");
        int index = 1;
        for (String column : table.columns()) {
          String field = table.name() + "." + column;
          if (column.equals("image_content")) {
            byte[] value = encryption.encrypt(sessionId, field, result.getBytes(column));
            if (value == null) {
              update.setNull(index++, Types.BLOB);
            } else {
              update.setBytes(index++, value);
            }
          } else {
            update.setString(index++, encryption.encrypt(sessionId, field, result.getString(column)));
          }
        }
        for (String key : table.keys()) {
          update.setObject(index++, result.getObject(key));
        }
        update.executeUpdate();
      }
    }
  }

  private record EncryptedTable(String name, List<String> keys, List<String> columns) { }
}
