package nl.llm.storyteller.api.bundle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nl.llm.storyteller.api.persistence.SessionRecord;
import nl.llm.storyteller.core.JsonSupport;
import nl.llm.storyteller.core.graph.KnowledgeGraphValidator;
import nl.llm.storyteller.core.graph.model.KnowledgeGraphDocument;
import nl.llm.storyteller.core.graph.persistence.KnowledgeGraphJsonCodec;
import nl.llm.storyteller.core.model.HistoryState;
import nl.llm.storyteller.core.model.Message;
import nl.llm.storyteller.core.model.TurnState;
import nl.llm.storyteller.core.service.TurnStateJsonCodec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static nl.llm.storyteller.core.service.TurnStateJsonCodec.PROTAGONISTS;
import static nl.llm.storyteller.core.service.TurnStateJsonCodec.ROUND_NUMBER;
import static nl.llm.storyteller.core.service.TurnStateJsonCodec.STARTED;
import static nl.llm.storyteller.core.service.TurnStateJsonCodec.TRIGGER_WORD;
import static nl.llm.storyteller.core.service.TurnStateJsonCodec.TURNS_THIS_ROUND;

public final class SessionBundleService {
  public static final long MAX_ARCHIVE_BYTES = 32L * 1024 * 1024;
  private static final long MAX_UNCOMPRESSED_BYTES = 64L * 1024 * 1024;
  private static final int MAX_MESSAGES = 10_000;
  private static final int MAX_TEXT_LENGTH = 1_000_000;
  private static final int MAX_NAME_LENGTH = 255;
  private static final int BUFFER_SIZE = 8192;
  private static final String MEMORY_DIRECTORY = "memory/";
  private static final String MACOS_METADATA_DIRECTORY = "__MACOSX/";
  private static final String MACOS_FINDER_METADATA = ".DS_Store";
  private static final String MACOS_APPLE_DOUBLE_PREFIX = "._";
  private static final String IMPORTED_STORY = "Imported story";
  private static final String BUNDLE_FORMAT = "storyteller-session";
  private static final String ARCHIVE_EXPANSION_ERROR = "Session ZIP expands beyond 64 MB.";
  private static final int BUNDLE_VERSION = 1;
  private static final String MANIFEST = "manifest.json";
  private static final String HISTORY = "history.json";
  private static final String SUMMARY = "summary.md";
  private static final String RECENT_SUMMARY = "recent-summary.md";
  private static final String CANONICAL_STATE = "canonical-state.yaml";
  private static final String TURN_STATE = "turn-state.json";
  private static final String KNOWLEDGE_GRAPH = "knowledge-graph.json";
  private static final String MESSAGES = "messages";
  private static final String ROLE = "role";
  private static final String CONTENT = "content";
  private static final String SUMMARY_CURSOR = "summary_cursor";
  private static final String RECENT_SUMMARY_CURSOR = "recent_summary_cursor";
  private static final String CANONICAL_STATE_CURSOR = "canonical_state_cursor";
  private static final String FORMAT = "format";
  private static final String VERSION = "version";
  private static final String TITLE = "title";
  private static final Set<String> ALLOWED_ENTRIES = Set.of(
    MANIFEST, HISTORY, SUMMARY, RECENT_SUMMARY, CANONICAL_STATE, TURN_STATE, KNOWLEDGE_GRAPH
  );
  private static final ObjectWriter PRETTY_JSON = JsonSupport.OBJECT_MAPPER.writerWithDefaultPrettyPrinter();

  private final SessionBundleRepository repository;
  private final Duration inactivityTimeout;
  private final Clock clock;
  private final Supplier<String> idSupplier;
  private final KnowledgeGraphJsonCodec graphCodec = new KnowledgeGraphJsonCodec();
  private final KnowledgeGraphValidator graphValidator = new KnowledgeGraphValidator();

  public SessionBundleService(SessionBundleRepository repository, Duration inactivityTimeout) {
    this(repository, inactivityTimeout, Clock.systemUTC(), () -> UUID.randomUUID().toString());
  }

  SessionBundleService(
    SessionBundleRepository repository,
    Duration inactivityTimeout,
    Clock clock,
    Supplier<String> idSupplier
  ) {
    this.repository = repository;
    this.inactivityTimeout = inactivityTimeout;
    this.clock = clock;
    this.idSupplier = idSupplier;
  }

  public SessionRecord importArchive(InputStream input, long archiveSize, String filename) throws IOException {
    if (input == null) {
      throw new IllegalArgumentException("Select a session ZIP to import.");
    }
    if (archiveSize < 1 || archiveSize > MAX_ARCHIVE_BYTES) {
      throw new IllegalArgumentException("Session ZIP must be between 1 byte and 32 MB.");
    }
    ParsedBundle parsed;
    try {
      parsed = decodeArchive(input);
    } catch (ZipException ex) {
      throw new IllegalArgumentException("Session ZIP is corrupt: " + ex.getMessage(), ex);
    }
    Instant now = clock.instant();
    SessionRecord session = new SessionRecord(
      idSupplier.get(), importedTitle(parsed.title(), filename), now, now, now,
      now.plus(inactivityTimeout), false
    );
    repository.create(session, parsed.bundle());
    return session;
  }

  public byte[] exportArchive(String sessionId, String title) throws IOException {
    return encodeArchive(repository.load(sessionId), title);
  }

  private ParsedBundle decodeArchive(InputStream input) throws IOException {
    Map<String, byte[]> entries = readEntries(input);
    byte[] historyBytes = entries.get(HISTORY);
    if (historyBytes == null) {
      throw new IllegalArgumentException("Session ZIP is missing history.json.");
    }
    HistoryState history = parseHistory(text(historyBytes, HISTORY));
    KnowledgeGraphDocument graph = parseGraph(entries.get(KNOWLEDGE_GRAPH));
    SessionBundle bundle = new SessionBundle(
      history,
      optionalText(entries, SUMMARY),
      optionalText(entries, RECENT_SUMMARY),
      optionalText(entries, CANONICAL_STATE),
      parseTurnState(entries.get(TURN_STATE)),
      graph
    );
    return new ParsedBundle(bundle, manifestTitle(entries.get(MANIFEST)));
  }

  private Map<String, byte[]> readEntries(InputStream input) throws IOException {
    Map<String, byte[]> entries = new HashMap<>();
    long totalBytes = 0;
    try (ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        String name = normalizedEntryName(entry);
        if (name == null) {
          zip.closeEntry();
          continue;
        }
        validateEntry(entry, name, entries);
        byte[] content = readEntry(zip, MAX_UNCOMPRESSED_BYTES - totalBytes);
        totalBytes += content.length;
        entries.put(name, content);
        zip.closeEntry();
      }
    }
    return entries;
  }

  private String normalizedEntryName(ZipEntry entry) {
    String name = entry.getName();
    if (name.equals(MACOS_METADATA_DIRECTORY) || name.startsWith(MACOS_METADATA_DIRECTORY)) {
      return null;
    }
    if (entry.isDirectory() && MEMORY_DIRECTORY.equals(name)) {
      return null;
    }
    if (name.startsWith(MEMORY_DIRECTORY)) {
      name = name.substring(MEMORY_DIRECTORY.length());
    }
    if (MACOS_FINDER_METADATA.equals(name) || name.startsWith(MACOS_APPLE_DOUBLE_PREFIX)) {
      return null;
    }
    if (name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) {
      throw new IllegalArgumentException("Unsafe session ZIP entry: " + entry.getName());
    }
    return name;
  }

  private void validateEntry(ZipEntry entry, String name, Map<String, byte[]> entries) {
    if (entry.isDirectory() || !ALLOWED_ENTRIES.contains(name)) {
      throw new IllegalArgumentException("Unsupported session ZIP entry: " + name);
    }
    if (entries.containsKey(name)) {
      throw new IllegalArgumentException("Duplicate session ZIP entry: " + name);
    }
  }

  private byte[] readEntry(InputStream input, long remainingBytes) throws IOException {
    if (remainingBytes <= 0) {
      throw new IllegalArgumentException(ARCHIVE_EXPANSION_ERROR);
    }
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[BUFFER_SIZE];
    int count;
    while ((count = input.read(buffer)) >= 0) {
      if (output.size() + count > remainingBytes) {
        throw new IllegalArgumentException(ARCHIVE_EXPANSION_ERROR);
      }
      output.write(buffer, 0, count);
    }
    return output.toByteArray();
  }

  private HistoryState parseHistory(String json) {
    try {
      JsonNode root = JsonSupport.OBJECT_MAPPER.readTree(json);
      JsonNode messagesNode = root.get(MESSAGES);
      if (messagesNode == null || !messagesNode.isArray()) {
        throw new IllegalArgumentException("history.json does not contain a valid messages array.");
      }
      if (messagesNode.size() > MAX_MESSAGES) {
        throw new IllegalArgumentException(HISTORY + " contains more than " + MAX_MESSAGES + " messages.");
      }
      List<Message> messages = new ArrayList<>();
      for (JsonNode node : messagesNode) {
        messages.add(readMessage(node, messages.size()));
      }
      validateTurns(messages);
      int summaryCursor = cursor(root, SUMMARY_CURSOR, messages.size());
      int recentSummaryCursor = cursor(root, RECENT_SUMMARY_CURSOR, messages.size());
      int canonicalStateCursor = cursor(root, CANONICAL_STATE_CURSOR, messages.size());
      return new HistoryState(messages, summaryCursor, recentSummaryCursor, canonicalStateCursor);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Invalid JSON in history.json: " + ex.getOriginalMessage(), ex);
    }
  }

  private Message readMessage(JsonNode node, int index) {
    if (!node.isObject() || !node.path(ROLE).isTextual() || !node.path(CONTENT).isTextual()) {
      throw new IllegalArgumentException("Invalid message at index " + index + " in history.json.");
    }
    String content = node.path(CONTENT).textValue();
    if (content.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException(
        "Message at index " + index + " exceeds " + MAX_TEXT_LENGTH + " characters."
      );
    }
    return new Message(node.path(ROLE).textValue(), content);
  }

  private void validateTurns(List<Message> messages) {
    if (messages.size() % 2 != 0) {
      throw new IllegalArgumentException("history.json must contain complete user and assistant pairs.");
    }
    for (int index = 0; index < messages.size(); index++) {
      String expectedRole = index % 2 == 0 ? "user" : "assistant";
      if (!expectedRole.equals(messages.get(index).role())) {
        throw new IllegalArgumentException(
          "history.json message " + index + " must have role " + expectedRole + "."
        );
      }
    }
  }

  private int cursor(JsonNode root, String name, int messageCount) {
    JsonNode value = root.get(name);
    int cursor = value == null ? 0 : value.asInt(-1);
    if (cursor < 0 || cursor > messageCount) {
      throw new IllegalArgumentException("history.json contains an invalid " + name + ".");
    }
    return cursor;
  }

  private KnowledgeGraphDocument parseGraph(byte[] content) {
    if (content == null) {
      return KnowledgeGraphDocument.empty();
    }
    try {
      KnowledgeGraphDocument graph = graphCodec.fromJson(text(content, KNOWLEDGE_GRAPH));
      graphValidator.validate(graph);
      return graph;
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException(
        "Invalid JSON in knowledge-graph.json: " + ex.getOriginalMessage(), ex
      );
    }
  }

  private TurnState parseTurnState(byte[] content) {
    if (content == null) {
      return TurnState.inactive();
    }
    try {
      JsonNode root = JsonSupport.OBJECT_MAPPER.readTree(text(content, TURN_STATE));
      String triggerWord = root.path(TRIGGER_WORD).asText("");
      boolean started = root.path(STARTED).asBoolean(false);
      int roundNumber = root.path(ROUND_NUMBER).asInt(0);
      if (triggerWord.length() > MAX_NAME_LENGTH || roundNumber < 0) {
        throw new IllegalArgumentException("turn-state.json contains invalid turn metadata.");
      }
      JsonNode protagonistsNode = root.path(PROTAGONISTS);
      if (!protagonistsNode.isArray()) {
        throw new IllegalArgumentException("turn-state.json protagonists must be an array.");
      }
      List<String> protagonists = new ArrayList<>();
      Set<String> uniqueNames = new HashSet<>();
      for (JsonNode node : protagonistsNode) {
        String name = node.isTextual() ? node.textValue().trim() : "";
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH || !uniqueNames.add(name)) {
          throw new IllegalArgumentException("turn-state.json contains an invalid protagonist.");
        }
        protagonists.add(name);
      }
      Map<String, Integer> turns = readTurns(root.path(TURNS_THIS_ROUND), uniqueNames);
      return new TurnState(triggerWord, started, roundNumber, List.copyOf(protagonists), Map.copyOf(turns));
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Invalid JSON in turn-state.json: " + ex.getOriginalMessage(), ex);
    }
  }

  private Map<String, Integer> readTurns(JsonNode node, Set<String> protagonists) {
    if (node.isMissingNode() || node.isNull()) {
      Map<String, Integer> emptyTurns = new HashMap<>();
      protagonists.forEach(protagonist -> emptyTurns.put(protagonist, 0));
      return emptyTurns;
    }
    if (!node.isObject()) {
      throw new IllegalArgumentException("turn-state.json turns_this_round must be an object.");
    }
    Map<String, Integer> turns = new HashMap<>();
    node.properties().forEach(entry -> {
      if (!protagonists.contains(entry.getKey()) || !entry.getValue().canConvertToInt()
        || entry.getValue().intValue() < 0) {
        throw new IllegalArgumentException("turn-state.json contains an invalid turn counter.");
      }
      turns.put(entry.getKey(), entry.getValue().intValue());
    });
    for (String protagonist : protagonists) {
      turns.putIfAbsent(protagonist, 0);
    }
    return turns;
  }

  private byte[] encodeArchive(SessionBundle bundle, String title) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
      writeEntry(zip, MANIFEST, manifestJson(title));
      writeEntry(zip, HISTORY, historyJson(bundle.history()));
      writeOptionalEntry(zip, SUMMARY, bundle.summary());
      writeOptionalEntry(zip, RECENT_SUMMARY, bundle.recentSummary());
      writeOptionalEntry(zip, CANONICAL_STATE, bundle.canonicalState());
      writeEntry(zip, TURN_STATE, turnStateJson(bundle.turnState()));
      writeEntry(zip, KNOWLEDGE_GRAPH, prettyJson(graphCodec.toJson(bundle.knowledgeGraph())));
    }
    return output.toByteArray();
  }

  private String manifestJson(String title) throws JsonProcessingException {
    ObjectNode manifest = JsonSupport.OBJECT_MAPPER.createObjectNode();
    manifest.put(FORMAT, BUNDLE_FORMAT);
    manifest.put(VERSION, BUNDLE_VERSION);
    if (title == null) {
      manifest.putNull(TITLE);
    } else {
      manifest.put(TITLE, title);
    }
    return prettyJson(manifest);
  }

  private String manifestTitle(byte[] content) {
    if (content == null) {
      return null;
    }
    try {
      JsonNode manifest = JsonSupport.OBJECT_MAPPER.readTree(text(content, MANIFEST));
      if (!BUNDLE_FORMAT.equals(manifest.path(FORMAT).asText())
        || manifest.path(VERSION).asInt(-1) != BUNDLE_VERSION) {
        throw new IllegalArgumentException("manifest.json uses an unsupported session bundle format.");
      }
      JsonNode title = manifest.get(TITLE);
      if (title == null || title.isNull()) {
        return null;
      }
      if (!title.isTextual() || title.textValue().length() > MAX_NAME_LENGTH) {
        throw new IllegalArgumentException("manifest.json contains an invalid title.");
      }
      return title.textValue().trim();
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Invalid JSON in manifest.json: " + ex.getOriginalMessage(), ex);
    }
  }

  private String historyJson(HistoryState history) throws JsonProcessingException {
    ObjectNode root = JsonSupport.OBJECT_MAPPER.createObjectNode();
    var messages = root.putArray(MESSAGES);
    for (Message message : history.messages()) {
      ObjectNode node = messages.addObject();
      node.put(ROLE, message.role());
      node.put(CONTENT, message.content());
    }
    root.put(SUMMARY_CURSOR, history.summaryCursor());
    root.put(RECENT_SUMMARY_CURSOR, history.recentSummaryCursor());
    root.put(CANONICAL_STATE_CURSOR, history.canonicalStateCursor());
    return prettyJson(root);
  }

  private String turnStateJson(TurnState state) throws JsonProcessingException {
    return prettyJson(TurnStateJsonCodec.toJson(state));
  }

  private String prettyJson(Object value) throws JsonProcessingException {
    return PRETTY_JSON.writeValueAsString(value);
  }

  private void writeOptionalEntry(ZipOutputStream zip, String name, String content) throws IOException {
    if (content != null) {
      writeEntry(zip, name, content);
    }
  }

  private void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  private String optionalText(Map<String, byte[]> entries, String name) {
    byte[] value = entries.get(name);
    return value == null ? null : text(value, name);
  }

  private String text(byte[] value, String name) {
    String text = new String(value, StandardCharsets.UTF_8);
    if (text.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException(name + " exceeds " + MAX_TEXT_LENGTH + " characters.");
    }
    return text;
  }

  private String importedTitle(String manifestTitle, String filename) {
    if (manifestTitle != null && !manifestTitle.isBlank()) {
      return manifestTitle;
    }
    String title = filename == null ? IMPORTED_STORY : filename.trim();
    if (title.toLowerCase(Locale.ROOT).endsWith(".zip")) {
      title = title.substring(0, title.length() - 4);
    }
    if (title.isBlank()) {
      return IMPORTED_STORY;
    }
    return title.length() <= MAX_NAME_LENGTH ? title : title.substring(0, MAX_NAME_LENGTH);
  }

  private record ParsedBundle(SessionBundle bundle, String title) { }
}
