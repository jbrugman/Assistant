package nl.jbrugman.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HistoryStore {
    private final Path path;
    private final Path legacyPath;

    HistoryStore(Path path, Path legacyPath) {
        this.path = path;
        this.legacyPath = legacyPath;
    }

    synchronized HistoryState load() {
        migrateLegacyHistoryIfNeeded();

        if (!Files.exists(path)) {
            return HistoryState.empty();
        }

        try {
            JsonNode data = JsonSupport.OBJECT_MAPPER.readTree(Files.readString(path, StandardCharsets.UTF_8));
            JsonNode messagesNode = data.get("messages");
            if (messagesNode == null || !messagesNode.isArray()) {
                throw new IllegalArgumentException("history.json heeft geen geldige 'messages' lijst.");
            }

            List<Message> messages = new ArrayList<>();
            for (JsonNode node : messagesNode) {
                messages.add(new Message(node.path("role").asText(), node.path("content").asText()));
            }

            int summaryCursor = data.path("summary_cursor").asInt(0);
            int recentSummaryCursor = data.path("recent_summary_cursor").asInt(0);
            int canonicalStateCursor = data.path("canonical_state_cursor").asInt(0);
            return new HistoryState(messages, summaryCursor, recentSummaryCursor, canonicalStateCursor);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Ongeldige JSON in " + path + ": " + ex.getOriginalMessage(), ex);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    synchronized void save(HistoryState state) {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, String>> messages = state.messages().stream().map(Message::toMap).toList();
        data.put("messages", messages);
        data.put("summary_cursor", state.summaryCursor());
        data.put("recent_summary_cursor", state.recentSummaryCursor());
        data.put("canonical_state_cursor", state.canonicalStateCursor());

        try {
            JsonSupport.OBJECT_MAPPER.writeValue(path.toFile(), data);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    synchronized void appendTurn(String userInput, String assistantResponse) {
        HistoryState state = load();
        List<Message> messages = new ArrayList<>(state.messages());
        messages.add(new Message("user", userInput));
        messages.add(new Message("assistant", assistantResponse));
        save(new HistoryState(messages, state.summaryCursor(), state.recentSummaryCursor(), state.canonicalStateCursor()));
    }

    synchronized List<Message> recentMessages(int limitTurns) {
        return sliceRecentCompleteTurns(load().messages(), limitTurns);
    }

    synchronized List<Message> recentMessagesWindow(int totalTurns, int trailingTurnsToExclude) {
        List<Message> messages = load().messages();
        List<Message> upToTotalTurns = sliceRecentCompleteTurns(messages, totalTurns);
        if (trailingTurnsToExclude <= 0 || upToTotalTurns.isEmpty()) {
            return upToTotalTurns;
        }

        List<Message> trailingMessages = sliceRecentCompleteTurns(messages, trailingTurnsToExclude);
        int trailingCount = trailingMessages.size();
        if (trailingCount <= 0) {
            return upToTotalTurns;
        }

        int endIndexExclusive = Math.max(0, upToTotalTurns.size() - trailingCount);
        return new ArrayList<>(upToTotalTurns.subList(0, endIndexExclusive));
    }

    synchronized void markSummarized(int messagesCount) {
        HistoryState state = load();
        int safeCount = Math.min(messagesCount, state.messages().size());
        save(new HistoryState(new ArrayList<>(state.messages()), safeCount, state.recentSummaryCursor(), state.canonicalStateCursor()));
    }

    synchronized void markRecentSummarized(int messagesCount) {
        HistoryState state = load();
        int safeCount = Math.min(messagesCount, state.messages().size());
        save(new HistoryState(new ArrayList<>(state.messages()), state.summaryCursor(), safeCount, state.canonicalStateCursor()));
    }

    synchronized void markCanonicalStateUpdated(int messagesCount) {
        HistoryState state = load();
        int safeCount = Math.min(messagesCount, state.messages().size());
        save(new HistoryState(new ArrayList<>(state.messages()), state.summaryCursor(), state.recentSummaryCursor(), safeCount));
    }

    private List<Message> sliceRecentCompleteTurns(List<Message> messages, int limitTurns) {
        if (limitTurns <= 0 || messages.isEmpty()) {
            return List.of();
        }

        int userSeen = 0;
        int startIndex = messages.size();
        for (int index = messages.size() - 1; index >= 0; index--) {
            if ("user".equals(messages.get(index).role())) {
                userSeen++;
                startIndex = index;
                if (userSeen >= limitTurns) {
                    break;
                }
            }
        }

        return new ArrayList<>(messages.subList(startIndex, messages.size()));
    }

    private void migrateLegacyHistoryIfNeeded() {
        if (Files.exists(path) || !Files.exists(legacyPath)) {
            return;
        }

        List<Message> messages = new ArrayList<>();
        String currentRole = null;
        List<String> buffer = new ArrayList<>();

        try {
            for (String line : Files.readAllLines(legacyPath, StandardCharsets.UTF_8)) {
                if (line.startsWith("USER: ")) {
                    flushLegacyMessage(messages, currentRole, buffer);
                    currentRole = "user";
                    buffer = new ArrayList<>(List.of(line.substring("USER: ".length())));
                } else if (line.startsWith("ASSISTANT: ")) {
                    flushLegacyMessage(messages, currentRole, buffer);
                    currentRole = "assistant";
                    buffer = new ArrayList<>(List.of(line.substring("ASSISTANT: ".length())));
                } else if (currentRole != null) {
                    buffer.add(line);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        flushLegacyMessage(messages, currentRole, buffer);
        save(new HistoryState(messages, 0, 0, 0));
    }

    private void flushLegacyMessage(List<Message> messages, String role, List<String> buffer) {
        if (role == null) {
            return;
        }

        String content = String.join("\n", buffer).trim();
        if (!content.isEmpty()) {
            messages.add(new Message(role, content));
        }
    }
}
