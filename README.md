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


# Future improvements - TODO's

1. [AssistantApp.java (line 20)](~/Assistant/src/main/java/nl/llm/storyteller/AssistantApp.java:20) is still carrying too many responsibilities. It owns CLI input, output formatting, shortcut wiring, prompt assembly, request execution, validation orchestration, and memory refresh triggering. That makes future changes high-risk because almost every feature crosses this file. The heaviest improvement would be to split this into a small composition root plus focused services like StorySessionService, PromptAssemblyService, and TerminalUi. I would prioritize this over adding more records first.

2. [AppConfig.java (line 11)](~/Assistant/src/main/java/nl/llm/storyteller/AppConfig.java:11) has become a large god-object for loading, merging, path resolution, validation, UI text, and runtime options. It works, but it is now the second main coupling hotspot after AssistantApp. A stronger boundary between ConfigLoader, ResolvedPaths, ModelSettings, and UiTextConfig would reduce accidental breakage and make tests much easier.

3. [PromptLoader.java (line 3)](~/Assistant/src/main/java/nl/llm/storyteller/PromptLoader.java:3) is doing more than “loading”: it also formats prompt templates, injects protagonist data, and builds validation request payloads. That is a sign it wants to become a small prompt service layer rather than a file loader. I would rename/split it into a low-level resource loader plus a higher-level prompt/template assembler.

4. [ResponseGuard.java (line 9)](~/Assistant/src/main/java/nl/llm/storyteller/ResponseGuard.java:9) mixes three concerns that will likely evolve independently: calling the validator model, parsing validator output, and post-processing candidate text. The current shape is still manageable, but if you add richer rule decisions later, this will get messy fast. A cleaner design would separate ValidationClient, ValidationDecisionParser, and ResponseSanitizer.

5. The summary managers are structurally duplicated: [SummaryManager.java (line 10)](~/Assistant/src/main/java/nl/llm/storyteller/SummaryManager.java:10), [RecentSummaryManager.java (line 10)](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/RecentSummaryManager.java:10), and [CanonicalStateManager.java (line 10)](/Users/jbrugman/Assistant/src/main/java/nl/llm/storyteller/CanonicalStateManager.java:10). Right now the duplication is still understandable, but it is the clearest maintenance hotspot in the architecture. If one concurrency or cursor bug is found, it likely needs fixing in three places. I would not rush into abstraction, but a shared AbstractDerivedMemoryManager or strategy-based updater would likely pay off soon.

6. There is still not much domain modeling around story concepts. You asked about more model records, and I think the answer is “some, but selectively.” Message and HistoryState are good starts, but things like fixed protagonists, hard constraints, validation request context, and assembled chat context are still mostly plain strings. I would add records where they represent stable domain concepts, for example PromptContext, ValidationRequest, or DerivedMemorySlice, before adding a broad “service layer everywhere.”

7. The tests are still thin relative to the amount of orchestration logic now in the app. The current tests cover some core behavior, which is good, but the highest-risk flows are still mostly untested: prompt assembly order, config override precedence end-to-end, derived memory update cutoffs, and validator bypass behavior. I would invest there before doing large refactors.

Priority:
1. Extract orchestration out of AssistantApp.
2. Split config loading/resolution from the AppConfig runtime view.
3. Introduce a prompt assembly service.
4. Reduce duplication across the three derived-memory managers.
5. Add a few focused domain records where strings are currently standing in for concepts.
