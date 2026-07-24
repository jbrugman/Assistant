# Storyteller

A local Java CLI storyteller that talks to LM Studio through the OpenAI-compatible endpoint at `http://localhost:1234`.

The project now uses a clean filesystem split:
- `systemprompts/`: prompts, protagonist setup, and the committed default `application.config`
- `memory/`: runtime memory files such as history, summaries, and canonical state

## Requirements

- Java 17+
- Maven
- LM Studio running with a loaded chat or instruct model

## Run Locally

### Recommended

```bash
cd /Users/jbrugman/Assistant
mvn -q package
java -jar target/storyteller-1.0.0-SNAPSHOT.jar
```

### Native Build

```bash
cd /Users/jbrugman/Assistant
mvn -Pnative -DskipTests package
```

The native binary is normally written to:

```text
target/storyteller
```

Run it from the project root:

```bash
cd /Users/jbrugman/Assistant
./target/storyteller
```

If an `application.config` file exists next to the native executable, it is loaded as a runtime override on top of `systemprompts/application.config`.

### Development Run

```bash
cd /Users/jbrugman/Assistant
mvn -q compile dependency:copy-dependencies
java -cp "target/classes:target/dependency/*" nl.llm.storyteller.AssistantApp
```

## Terminal Shortcuts

- `Ctrl-G`: sends `(continue the story)`
- `Ctrl-W`: sends a reset instruction that tells the model to strictly follow the active story rules again

On macOS, `Cmd-G` and `Cmd-W` only work if the terminal forwards those key combinations as meta or escape input.

## Repository Layout

### `systemprompts/`

- `application.config`: the committed default configuration
- `systemprompt.md`: main storyteller prompt
- `rules.md`: optional validator prompt
- `fixed_protagonists.yml`: stable protagonist baseline
- `summarysystemprompt.md`: long-term summary prompt
- `recentsummarysystemprompt.md`: recent-summary prompt
- `canonicalstatesystemprompt.md`: canonical-state prompt

### `memory/`

- `history.json`: full conversation history
- `history.md`: legacy import source, if present
- `summary.md`: long-term story memory
- `recent-summary.md`: compact recent-context memory
- `canonical-state.yaml`: confirmed current canon

These memory files may start out missing. The app creates and updates them as needed.

## Configuration

All committed default values now live in [systemprompts/application.config](/Users/jbrugman/Assistant/systemprompts/application.config:1). Java no longer carries fallback defaults for model choice, file paths, prompt content, or tuning values.

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

When validation is disabled, `systemprompts/rules.md` is skipped and the raw model answer is returned directly.

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
