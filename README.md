# Storyteller

A local Java CLI storyteller that talks to a local OpenAI-compatible chat endpoint, such as LM Studio, Jan.ai, or a similar tool.
Started as a small Assistant api, but turned in the storyteller assistant, way more fun that way.

## Requirements

- Java 25+
- Maven
- A local OpenAI-compatible chat server running with a loaded model, such as LM Studio, Jan.ai, or a similar tool

## Packaging Behavior

The app ships with working built-in defaults.

Bundled defaults live in:
- `src/main/resources/systemprompts/`

Those files are compiled into:
- the runnable jar
- the native executable

At runtime, local filesystem overrides take precedence when present:
- `./systemprompts/application.config`
- `./systemprompts/*.md`
- `./systemprompts/*.yml`

So the behavior is:
1. use bundled defaults from the build artifact
2. override them with local files in `./systemprompts/` when they exist
3. keep runtime memory in `./memory/`

## Run Locally

### Recommended

```bash
cd ~/Assistant
mvn -q package
java -jar target/storyteller-1.0.0-SNAPSHOT.jar
```

### Native Build

```bash
cd ~/Assistant
mvn -Pnative -DskipTests package
```

The native binary is normally written to:

```text
target/storyteller
```

Run it from the project root:

```bash
cd ~/Assistant
./target/storyteller
```

If an `application.config` file exists next to the native executable, it is loaded as an additional runtime override.

### Development Run

```bash
cd ~/Assistant
mvn -q compile dependency:copy-dependencies
java -cp "target/classes:target/dependency/*" nl.llm.storyteller.AssistantApp
```

## Terminal Shortcuts

- `Ctrl-G`: sends `(continue the story)`
- `Ctrl-W`: sends a reset instruction that tells the model to strictly follow the active story rules again

On macOS, `Cmd-G` and `Cmd-W` only work if the terminal forwards those key combinations as meta or escape input.

## Runtime Files

### Bundled defaults

These are compiled into the app from `src/main/resources/systemprompts/`:
- `application.config`
- `systemprompt.md`
- `rules.md`
- `fixed_protagonists.yml`
- `fixedprotagonistscontext.md`
- `summarysystemprompt.md`
- `summarycontext.md`
- `recentsummarysystemprompt.md`
- `recentsummarycontext.md`
- `canonicalstatesystemprompt.md`
- `canonicalstatecontext.md`
- `validationsystemprompt.md`
- `validationrequesttemplate.md`

### Optional local overrides

If you create a local `systemprompts/` folder in the working directory with files of the same names, those files override the bundled defaults.

### Runtime memory

The app reads and writes story memory in `memory/`:
- `history.json`
- `history.md`
- `summary.md`
- `recent-summary.md`
- `canonical-state.yaml`

These files may start out missing. The app creates and updates them as needed.

## Configuration

All default configuration values now come from bundled `application.config`, not hard-coded Java defaults.

Configuration is now split into:
- [`AppConfigLoader.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/AppConfigLoader.java): loads bundled defaults, local overrides, and native-runtime overrides
- [`AppConfigSource.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/AppConfigSource.java): typed access to merged raw properties
- [`AppConfig.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/AppConfig.java): validated runtime view used by the app

Important settings:
- `chat.maxRecentTurns=2`
- `recentSummary.maxRecentTurns=12`
- `recentSummary.batchMessages=6`
- `summary.batchMessages=10`
- `canonicalState.batchMessages=5`
- `validation.enabled=true`

If a model behaves badly with the rules engine, disable it with:

```properties
validation.enabled=false
```

When validation is disabled, `rules.md` is skipped and the raw model answer is returned directly.

## Prompt Assembly

The app does not send the full history back to the model. It sends:
- the main system prompt
- fixed protagonists
- canonical state
- long-term summary
- recent summary
- the last `chat.maxRecentTurns` raw turns
- the latest user message

## Memory Layers

The storyteller uses three derived memory layers beside the latest raw turns:
- `memory/recent-summary.md`: recent middle layer
- `memory/summary.md`: long-term background and continuity
- `memory/canonical-state.yaml`: compact confirmed canon

This keeps prompt size down while preserving continuity.

## Runtime Structure

The runtime is now split into two clear layers:
- [`AssistantApp.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/AssistantApp.java): terminal bootstrap, shortcut registration, input loop, and formatted output
- [`StorySessionService.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/service/StorySessionService.java): prompt assembly, model call, validation, history append, and derived-memory refresh triggering
- [`PromptAssemblyService.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/service/PromptAssemblyService.java): assembles the chat prompt stack and validation payload from prompts, memory, and recent turns

Configuration follows the same separation:
- [`AppConfigLoader.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/AppConfigLoader.java) and [`AppConfigSource.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/AppConfigSource.java): loading, merging, and path resolution
- [`AppConfig.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/AppConfig.java): validated runtime settings only

Validation is also split into focused parts:
- [`ValidationClient.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/service/ValidationClient.java): sends the validator prompt to the configured model
- [`ValidationDecisionParser.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/service/ValidationDecisionParser.java): extracts `ALLOW` or `BLOCK` from structured or plain-text validator output
- [`ResponseSanitizer.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/service/ResponseSanitizer.java): cleans visible JSON-style escapes before terminal output
- [`ResponseGuard.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/service/ResponseGuard.java): coordinates those parts and applies fail-closed behavior

The three derived-memory updaters now share one common infrastructure layer:
- [`DerivedMemoryManager.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/service/DerivedMemoryManager.java): worker lifecycle, concurrency guard, model-call flow, and safe write-back coordination
- [`SummaryManager.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/service/SummaryManager.java), [`RecentSummaryManager.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/service/RecentSummaryManager.java), and [`CanonicalStateManager.java`](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/service/CanonicalStateManager.java): their own cutoff rules and prompt contents


# Future improvements - TODO's

1. [PromptLoader.java (line 3)](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/service/PromptLoader.java:3) is still doing more than loading: it also formats prompt templates, injects protagonist data, and builds validation request payloads. Now that prompt assembly moved out of `StorySessionService`, this is the next good extraction candidate.

2. The tests are still thinner than the orchestration risk profile. The most valuable additions would now be derived-memory refresh cutoffs, background update cursor protection, and more end-to-end validator bypass behavior.
