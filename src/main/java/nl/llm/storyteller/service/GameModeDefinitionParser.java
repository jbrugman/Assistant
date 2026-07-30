package nl.llm.storyteller.service;

import nl.llm.storyteller.model.GameModeDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GameModeDefinitionParser {
    private static final Pattern TRIGGER_WORD_PATTERN = Pattern.compile("^\\s*trigger_word:\\s*\"?([^\"]+)\"?\\s*$");
    private static final Pattern PROTAGONIST_NAME_PATTERN = Pattern.compile("^\\s{2}-\\s*name:\\s*\"?([^\"]+)\"?\\s*$");

    public GameModeDefinition parse(String fixedProtagonistsYaml) {
        if (fixedProtagonistsYaml == null || fixedProtagonistsYaml.isBlank()) {
            return new GameModeDefinition("", List.of());
        }

        String triggerWord = "";
        List<String> protagonists = new ArrayList<>();
        boolean inFixedProtagonistSection = false;

        for (String line : fixedProtagonistsYaml.split("\\R")) {
            Matcher triggerMatcher = TRIGGER_WORD_PATTERN.matcher(line);
            if (triggerMatcher.matches()) {
                triggerWord = triggerMatcher.group(1).trim();
                continue;
            }

            if (line.startsWith("fixed_protagonist:")) {
                inFixedProtagonistSection = true;
                continue;
            }

            if (inFixedProtagonistSection && isTopLevelSection(line)) {
                inFixedProtagonistSection = false;
            }

            if (inFixedProtagonistSection) {
                Matcher protagonistMatcher = PROTAGONIST_NAME_PATTERN.matcher(line);
                if (protagonistMatcher.matches()) {
                    protagonists.add(protagonistMatcher.group(1).trim());
                }
            }
        }

        return new GameModeDefinition(triggerWord, protagonists);
    }

    private boolean isTopLevelSection(String line) {
        return !line.isBlank() && !Character.isWhitespace(line.charAt(0)) && line.endsWith(":");
    }
}
