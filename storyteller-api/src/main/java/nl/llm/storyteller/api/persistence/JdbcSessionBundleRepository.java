package nl.llm.storyteller.api.persistence;

import nl.llm.storyteller.api.bundle.SessionBundle;
import nl.llm.storyteller.api.bundle.SessionBundleRepository;
import nl.llm.storyteller.core.graph.model.Entity;
import nl.llm.storyteller.core.graph.model.EntityId;
import nl.llm.storyteller.core.graph.model.EntityType;
import nl.llm.storyteller.core.graph.model.Fact;
import nl.llm.storyteller.core.graph.model.FactSource;
import nl.llm.storyteller.core.graph.model.FactStatus;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.model.Polarity;
import nl.llm.storyteller.core.graph.model.PredicateId;
import nl.llm.storyteller.core.model.HistoryState;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.model.TurnState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static nl.llm.storyteller.api.persistence.SessionBundleQueries.INSERT_ALIAS;
import static nl.llm.storyteller.api.persistence.SessionBundleQueries.INSERT_ENTITY;
import static nl.llm.storyteller.api.persistence.SessionBundleQueries.INSERT_FACT;
import static nl.llm.storyteller.api.persistence.SessionBundleQueries.INSERT_GRAPH;
import static nl.llm.storyteller.api.persistence.SessionBundleQueries.INSERT_MEMORY;
import static nl.llm.storyteller.api.persistence.SessionBundleQueries.INSERT_PROTAGONIST;
import static nl.llm.storyteller.api.persistence.SessionBundleQueries.INSERT_TURN_STATE;
import static nl.llm.storyteller.api.persistence.SessionBundleQueries.SELECT_ALIASES;
import static nl.llm.storyteller.api.persistence.SessionBundleQueries.SELECT_ENTITIES;
import static nl.llm.storyteller.api.persistence.SessionBundleQueries.SELECT_FACTS;
import static nl.llm.storyteller.api.persistence.SessionBundleQueries.SELECT_GRAPH;
import static nl.llm.storyteller.api.persistence.SessionBundleQueries.SELECT_MEMORY;
import static nl.llm.storyteller.api.persistence.SessionBundleQueries.SELECT_MESSAGES;
import static nl.llm.storyteller.api.persistence.SessionBundleQueries.SELECT_PROTAGONISTS;
import static nl.llm.storyteller.api.persistence.SessionBundleQueries.SELECT_TURN_STATE;
import static nl.llm.storyteller.api.persistence.SessionQueries.INSERT_SESSION_CONFIGURATION;
import static nl.llm.storyteller.api.persistence.SessionPersistenceSupport.insertSession;
import static nl.llm.storyteller.api.persistence.SessionPersistenceSupport.insertSessionId;
import static nl.llm.storyteller.api.persistence.StoryQueries.INSERT_MESSAGE;

public final class JdbcSessionBundleRepository implements SessionBundleRepository {
  private final Database database;

  public JdbcSessionBundleRepository(Database database) {
    this.database = database;
  }

  @Override
  public SessionBundle load(String sessionId) {
    try (Connection connection = database.openConnection()) {
      MemoryState memory = loadMemory(connection, sessionId);
      return new SessionBundle(
        new HistoryState(loadMessages(connection, sessionId), memory.summaryCursor(),
          memory.recentSummaryCursor(), memory.canonicalStateCursor()),
        memory.summary(),
        memory.recentSummary(),
        memory.canonicalState(),
        loadTurnState(connection, sessionId),
        loadGraph(connection, sessionId)
      );
    } catch (SQLException ex) {
      throw new DatabaseException("Could not export session " + sessionId + ".", ex);
    }
  }

  @Override
  public void create(SessionRecord session, SessionBundle bundle) {
    try (Connection connection = database.openConnection()) {
      connection.setAutoCommit(false);
      createInTransaction(connection, session, bundle);
    } catch (SQLException ex) {
      throw new DatabaseException("Could not import story session.", ex);
    }
  }

  private void createInTransaction(Connection connection, SessionRecord session, SessionBundle bundle)
    throws SQLException {
    try {
      insertSession(connection, session);
      insertSessionId(connection, INSERT_SESSION_CONFIGURATION, session.sessionId());
      insertMemory(connection, session.sessionId(), bundle);
      insertTurnState(connection, session.sessionId(), bundle.turnState());
      insertMessages(connection, session.sessionId(), bundle.history().messages());
      insertGraph(connection, session.sessionId(), bundle.knowledgeGraph());
      connection.commit();
    } catch (SQLException ex) {
      rollback(connection, ex);
      throw ex;
    }
  }

  private MemoryState loadMemory(Connection connection, String sessionId) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(SELECT_MEMORY)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new SQLException("Session memory does not exist.");
        }
        return new MemoryState(
          resultSet.getString("summary_content"),
          resultSet.getString("recent_summary_content"),
          resultSet.getString("canonical_state_content"),
          resultSet.getInt("summary_cursor"),
          resultSet.getInt("recent_summary_cursor"),
          resultSet.getInt("canonical_state_cursor")
        );
      }
    }
  }

  private List<Message> loadMessages(Connection connection, String sessionId) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(SELECT_MESSAGES)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        List<Message> messages = new ArrayList<>();
        while (resultSet.next()) {
          messages.add(new Message(resultSet.getString("message_role"), resultSet.getString("content")));
        }
        return List.copyOf(messages);
      }
    }
  }

  private KnowledgeGraphDocument loadGraph(Connection connection, String sessionId) throws SQLException {
    int schemaVersion;
    long revision;
    try (PreparedStatement statement = connection.prepareStatement(SELECT_GRAPH)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new SQLException("Knowledge graph does not exist.");
        }
        schemaVersion = resultSet.getInt("schema_version");
        revision = resultSet.getLong("revision");
      }
    }
    Map<String, List<String>> aliases = loadAliases(connection, sessionId);
    return new KnowledgeGraphDocument(
      schemaVersion,
      revision,
      loadEntities(connection, sessionId, aliases),
      loadFacts(connection, sessionId)
    );
  }

  private TurnState loadTurnState(Connection connection, String sessionId) throws SQLException {
    String triggerWord;
    boolean started;
    int roundNumber;
    try (PreparedStatement statement = connection.prepareStatement(SELECT_TURN_STATE)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new SQLException("Turn state does not exist.");
        }
        triggerWord = resultSet.getString("trigger_word");
        started = resultSet.getBoolean("started");
        roundNumber = resultSet.getInt("round_number");
      }
    }
    List<String> protagonists = new ArrayList<>();
    Map<String, Integer> turns = new LinkedHashMap<>();
    try (PreparedStatement statement = connection.prepareStatement(SELECT_PROTAGONISTS)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          String name = resultSet.getString("protagonist_name");
          protagonists.add(name);
          turns.put(name, resultSet.getInt("turns_this_round"));
        }
      }
    }
    return new TurnState(triggerWord, started, roundNumber, List.copyOf(protagonists), Map.copyOf(turns));
  }

  private Map<String, List<String>> loadAliases(Connection connection, String sessionId) throws SQLException {
    Map<String, List<String>> aliases = new LinkedHashMap<>();
    try (PreparedStatement statement = connection.prepareStatement(SELECT_ALIASES)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          aliases.computeIfAbsent(resultSet.getString("entity_id"), _ -> new ArrayList<>())
            .add(resultSet.getString("alias_name"));
        }
      }
    }
    return aliases;
  }

  private Map<String, Entity> loadEntities(
    Connection connection,
    String sessionId,
    Map<String, List<String>> aliases
  ) throws SQLException {
    Map<String, Entity> entities = new LinkedHashMap<>();
    try (PreparedStatement statement = connection.prepareStatement(SELECT_ENTITIES)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          String entityId = resultSet.getString("entity_id");
          entities.put(entityId, new Entity(
            EntityType.valueOf(resultSet.getString("entity_type")),
            resultSet.getString("entity_name"),
            aliases.getOrDefault(entityId, List.of()),
            FactSource.valueOf(resultSet.getString("entity_source"))
          ));
        }
      }
    }
    return entities;
  }

  private List<Fact> loadFacts(Connection connection, String sessionId) throws SQLException {
    List<Fact> facts = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(SELECT_FACTS)) {
      statement.setString(1, sessionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          int sourceTurn = resultSet.getInt("source_turn");
          facts.add(new Fact(
            resultSet.getString("fact_id"),
            new EntityId(resultSet.getString("subject_entity_id")),
            new PredicateId(resultSet.getString("predicate_id")),
            new EntityId(resultSet.getString("object_entity_id")),
            Polarity.valueOf(resultSet.getString("polarity")),
            FactStatus.valueOf(resultSet.getString("fact_status")),
            FactSource.valueOf(resultSet.getString("fact_source")),
            resultSet.wasNull() ? null : sourceTurn,
            resultSet.getBoolean("is_hard")
          ));
        }
      }
    }
    return List.copyOf(facts);
  }

  private void insertMemory(Connection connection, String sessionId, SessionBundle bundle) throws SQLException {
    HistoryState history = bundle.history();
    try (PreparedStatement statement = connection.prepareStatement(INSERT_MEMORY)) {
      statement.setString(1, sessionId);
      statement.setString(2, bundle.summary());
      statement.setString(3, bundle.recentSummary());
      statement.setString(4, bundle.canonicalState());
      statement.setInt(5, history.summaryCursor());
      statement.setInt(6, history.recentSummaryCursor());
      statement.setInt(7, history.canonicalStateCursor());
      statement.executeUpdate();
    }
  }

  private void insertMessages(Connection connection, String sessionId, List<Message> messages) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(INSERT_MESSAGE)) {
      statement.setString(1, sessionId);
      for (int index = 0; index < messages.size(); index++) {
        Message message = messages.get(index);
        statement.setInt(2, index);
        statement.setString(3, message.role());
        statement.setString(4, message.content());
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private void insertTurnState(Connection connection, String sessionId, TurnState state) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(INSERT_TURN_STATE)) {
      statement.setString(1, sessionId);
      statement.setString(2, state.triggerWord());
      statement.setBoolean(3, state.started());
      statement.setInt(4, state.roundNumber());
      statement.executeUpdate();
    }
    try (PreparedStatement statement = connection.prepareStatement(INSERT_PROTAGONIST)) {
      statement.setString(1, sessionId);
      for (int index = 0; index < state.protagonists().size(); index++) {
        String protagonist = state.protagonists().get(index);
        statement.setInt(2, index);
        statement.setString(3, protagonist);
        statement.setInt(4, state.turnsThisRound().getOrDefault(protagonist, 0));
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private void insertGraph(
    Connection connection,
    String sessionId,
    KnowledgeGraphDocument graph
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(INSERT_GRAPH)) {
      statement.setString(1, sessionId);
      statement.setInt(2, graph.schemaVersion());
      statement.setLong(3, graph.revision());
      statement.executeUpdate();
    }
    insertEntities(connection, sessionId, graph);
    insertFacts(connection, sessionId, graph.facts());
  }

  private void insertEntities(
    Connection connection,
    String sessionId,
    KnowledgeGraphDocument graph
  ) throws SQLException {
    try (PreparedStatement entityStatement = connection.prepareStatement(INSERT_ENTITY);
         PreparedStatement aliasStatement = connection.prepareStatement(INSERT_ALIAS)) {
      entityStatement.setString(1, sessionId);
      aliasStatement.setString(1, sessionId);
      for (Map.Entry<String, Entity> entry : graph.entities().entrySet()) {
        Entity entity = entry.getValue();
        entityStatement.setString(2, entry.getKey());
        entityStatement.setString(3, entity.type().name());
        entityStatement.setString(4, entity.name());
        entityStatement.setString(5, entity.source().name());
        entityStatement.addBatch();
        aliasStatement.setString(2, entry.getKey());
        for (int index = 0; index < entity.aliases().size(); index++) {
          aliasStatement.setInt(3, index);
          aliasStatement.setString(4, entity.aliases().get(index));
          aliasStatement.addBatch();
        }
      }
      entityStatement.executeBatch();
      aliasStatement.executeBatch();
    }
  }

  private void insertFacts(Connection connection, String sessionId, List<Fact> facts) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(INSERT_FACT)) {
      statement.setString(1, sessionId);
      for (Fact fact : facts) {
        statement.setString(2, fact.id());
        statement.setString(3, fact.subject().value());
        statement.setString(4, fact.predicate().value());
        statement.setString(5, fact.object().value());
        statement.setString(6, fact.polarity().name());
        statement.setString(7, fact.status().name());
        statement.setString(8, fact.source().name());
        statement.setObject(9, fact.sourceTurn(), Types.INTEGER);
        statement.setBoolean(10, fact.hard());
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private void rollback(Connection connection, SQLException original) {
    try {
      connection.rollback();
    } catch (SQLException rollbackFailure) {
      original.addSuppressed(rollbackFailure);
    }
  }

  private record MemoryState(
    String summary,
    String recentSummary,
    String canonicalState,
    int summaryCursor,
    int recentSummaryCursor,
    int canonicalStateCursor
  ) { }
}
