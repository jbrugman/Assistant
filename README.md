# Storyteller

A local Java CLI storyteller that talks to LM Studio through the OpenAI-compatible endpoint at `http://localhost:1234`.

## Requirements

- Java 25+
- Maven
- LM Studio running with a loaded chat or instruct model

## Packaging Behavior

The app now ships with working built-in defaults.

Committed defaults live in:
- `src/main/resources/systemprompts/`

Those files are bundled into:
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

If an `application.config` file exists next to the native executable, it is still loaded as an additional runtime override.

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
