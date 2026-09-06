package nl.llm.storyteller.cli.benchmark;

import nl.llm.storyteller.core.ApplicationContext;
import nl.llm.storyteller.core.config.AppConfig;
import nl.llm.storyteller.core.config.AppConfigLoader;
import nl.llm.storyteller.core.service.PromptResourceLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;

final class BenchmarkWorkspace implements AutoCloseable {
  private final Path directory;
  private final AppConfig config;

  private BenchmarkWorkspace(Path directory, AppConfig config) {
    this.directory = directory;
    this.config = config;
  }

  static BenchmarkWorkspace create(ApplicationContext sourceContext, BenchmarkOptions options) throws IOException {
    Path directory = Files.createTempDirectory("storyteller-benchmark-");
    Path prompts = Files.createDirectories(directory.resolve("prompts"));
    Path memory = Files.createDirectories(directory.resolve("memory"));
    write(prompts.resolve("system.md"), "Keep a concise factual story. Never change an established fact unless the user explicitly changes it.");
    write(prompts.resolve("rules.md"), """
      - Answer fact questions exactly and briefly. Do not merge facts belonging to different people.
      - Preserve the binding world state and every hard constraint from fixed protagonists.
      - Alice is romantically and sexually attracted only to women. Never portray her as romantically or sexually attracted to a man, including Thomas or David.
      """);
    write(prompts.resolve("fixed.yml"), """
      fixed_protagonists:
        Alice:
          role: protagonist
          hard_constraints:
            romantic_and_sexual_attraction_only_toward_women: true
          traits:
            - romantically and sexually attracted only to women
      """);
    write(prompts.resolve("fixed-context.md"), "Fixed protagonists:\n%s");
    write(prompts.resolve("summary-system.md"), "Summarize established facts only.");
    write(prompts.resolve("summary-context.md"), "Summary:\n%s");
    write(prompts.resolve("recent-summary-system.md"), "Summarize recent established facts only.");
    write(prompts.resolve("recent-summary-context.md"), "Recent summary:\n%s");
    write(prompts.resolve("canonical-state-system.md"), "Record current established facts only.");
    write(prompts.resolve("canonical-state-context.md"), "Canonical state:\n%s");
    write(prompts.resolve("turn-single.md"), "%s");
    write(prompts.resolve("turn-party.md"), "%s");
    write(prompts.resolve("cache-buster.md"), "Ignore deterministic cache-buster token: %s");
    copyValidationPrompts(sourceContext, directory);

    Properties properties = new Properties();
    copyBackend(sourceContext, options, properties);
    properties.setProperty("file.systemPrompt", prompts.resolve("system.md").toString());
    properties.setProperty("file.rules", prompts.resolve("rules.md").toString());
    properties.setProperty("file.fixedProtagonists", prompts.resolve("fixed.yml").toString());
    properties.setProperty("file.fixedProtagonistsContext", prompts.resolve("fixed-context.md").toString());
    properties.setProperty("file.summarySystemPrompt", prompts.resolve("summary-system.md").toString());
    properties.setProperty("file.summaryContext", prompts.resolve("summary-context.md").toString());
    properties.setProperty("file.recentSummarySystemPrompt", prompts.resolve("recent-summary-system.md").toString());
    properties.setProperty("file.recentSummaryContext", prompts.resolve("recent-summary-context.md").toString());
    properties.setProperty("file.canonicalStateSystemPrompt", prompts.resolve("canonical-state-system.md").toString());
    properties.setProperty("file.canonicalStateContext", prompts.resolve("canonical-state-context.md").toString());
    properties.setProperty("file.validationSystemPrompt", directory.resolve("systemprompts/validationsystemprompt.md").toString());
    properties.setProperty("file.validationRequestTemplate", directory.resolve("systemprompts/validationrequesttemplate.md").toString());
    properties.setProperty("file.turnViolationSingleTemplate", prompts.resolve("turn-single.md").toString());
    properties.setProperty("file.turnViolationPartyTemplate", prompts.resolve("turn-party.md").toString());
    properties.setProperty("file.resetCacheBusterTemplate", prompts.resolve("cache-buster.md").toString());
    properties.setProperty("file.history", memory.resolve("history.json").toString());
    properties.setProperty("file.legacyHistory", memory.resolve("history.md").toString());
    properties.setProperty("file.summary", memory.resolve("summary.md").toString());
    properties.setProperty("file.recentSummary", memory.resolve("recent-summary.md").toString());
    properties.setProperty("file.canonicalState", memory.resolve("canonical-state.yaml").toString());
    properties.setProperty("file.turnState", memory.resolve("turn-state.json").toString());
    properties.setProperty("file.knowledgeGraph", memory.resolve("knowledge-graph.json").toString());
    properties.setProperty("chat.maxRecentTurns", "2");
    properties.setProperty("recentSummary.maxRecentTurns", "2");
    properties.setProperty("summary.batchMessages", "1000");
    properties.setProperty("recentSummary.batchMessages", "1000");
    properties.setProperty("canonicalState.batchMessages", "1000");
    properties.setProperty("graph.turnBased.batchTurns", options.knowledgeGraph() ? "5" : "1000");
    properties.setProperty("graph.enabled", Boolean.toString(options.knowledgeGraph()));
    properties.setProperty("cacheBuster.interval", options.cacheBuster() ? "5" : "0");
    properties.setProperty("cacheBuster.enabled", Boolean.toString(options.cacheBuster()));
    properties.setProperty("cacheBuster.tokenPrefix", "benchmark-42");
    properties.setProperty("validation.enabled", Boolean.toString(options.validation()));
    properties.setProperty("chat.temperature", "0");
    properties.setProperty("chat.topK", "1");
    properties.setProperty("chat.topP", "1");
    properties.setProperty("chat.minP", "0");
    properties.setProperty("chat.repeatPenalty", "1");
    properties.setProperty("chat.seed", "42");
    properties.setProperty("chat.maxTokens", "128");
    properties.setProperty("summary.temperature", "0");
    properties.setProperty("summary.topP", "1");
    properties.setProperty("summary.seed", "42");
    properties.setProperty("summary.maxTokens", "2048");
    properties.setProperty("validation.temperature", "0");
    properties.setProperty("validation.topP", "1");
    properties.setProperty("validation.seed", "42");
    properties.setProperty("validation.maxTokens", "128");

    Path override = directory.resolve("application.config");
    try (var writer = Files.newBufferedWriter(override)) {
      properties.store(writer, "Isolated deterministic benchmark configuration");
    }
    return new BenchmarkWorkspace(directory, AppConfigLoader.load(directory, override));
  }

  AppConfig config() {
    return config;
  }

  @Override
  public void close() throws IOException {
    try (var paths = Files.walk(directory)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }

  private static void copyBackend(ApplicationContext sourceContext, BenchmarkOptions options, Properties properties) {
    AppConfig source = sourceContext.config();
    properties.setProperty("backend.type", "openai-compatible");
    properties.setProperty("backend.http.url", activeBackendUrl(sourceContext));
    properties.setProperty("backend.http.apiKey", source.openAiCompatibleApiKey());
    properties.setProperty("model.chat", options.model());
    properties.setProperty("model.validator", options.model());
  }

  private static void copyValidationPrompts(ApplicationContext sourceContext, Path directory) throws IOException {
    PromptResourceLoader prompts = new PromptResourceLoader(sourceContext.config());
    Path systemPrompts = Files.createDirectories(directory.resolve("systemprompts"));
    write(systemPrompts.resolve("validationsystemprompt.md"), prompts.loadValidationSystemPrompt());
    write(systemPrompts.resolve("validationrequesttemplate.md"), prompts.loadValidationRequestTemplate());
  }

  private static String activeBackendUrl(ApplicationContext context) {
    if (context.managedMlxServer() != null) {
      return context.managedMlxServer().chatCompletionsUrl();
    }
    if (context.managedLlamaServer() != null) {
      return context.managedLlamaServer().chatCompletionsUrl();
    }
    return context.config().openAiCompatibleUrl();
  }

  private static void write(Path path, String content) throws IOException {
    Files.writeString(path, content + "\n");
  }
}
