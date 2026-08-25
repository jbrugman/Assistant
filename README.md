# Storyteller

A Java CLI storyteller that talks to an OpenAI-compatible chat endpoint, whether that endpoint runs locally or remotely.
It started as a small assistant app and gradually evolved into a dedicated storytelling tool.

A deliberate design choice was to keep the codebase framework-light rather than building it around Spring Boot or Quarkus.
The goal was to learn about LLM application design, prompting, memory shaping, and validation behavior, not to spend most of the project inside framework infrastructure.
That choice also helps keep the runtime and source layout relatively small and easy to inspect.

A LLM handled a meaningful share of the routine implementation work, while I remained responsible for the architecture, direction, constraints, review, and final decisions.

## Requirements

- Java 25+
- Maven
- An OpenAI-compatible chat server with a loaded or selectable model, such as LM Studio, Jan.ai, a self-hosted compatible endpoint, or a hosted compatible API

## Recommended setup
See: https://github.com/jbrugman/Assistant/wiki/Configuration-&-Hardware-Guide

## Used Tools / Hardware

- [Aider](https://github.com/Aider-AI/aider) for code generation and refactoring using a local llm-server
- [LM Studio](https://lmstudio.ai/docs/developer) for model management, local serving, and OpenAI-compatible API access
- [Jan](https://www.jan.ai/docs) for compatibility testing
- [ChatGPT](https://chatgpt.com/) for discussing design decisions and improving system prompts
- [mlx-community/gemma-4-26B-A4B-it-qat-6bit](https://huggingface.co/mlx-community/gemma-4-26B-A4B-it-qat-6bit) for local story-model testing
- [Qwen/Qwen3-Coder-30B-A3B-Instruct](https://huggingface.co/Qwen/Qwen3-Coder-30B-A3B-Instruct) as a local coding model during development
- [llama.cpp](https://github.com/ggml-org/llama.cpp) for running GGUF models through the managed local llama-server backend
- [mlx-vlm](https://github.com/Blaizzy/mlx-vlm) for running MLX models through the managed local `mlx_vlm.server` backend
- MacBook Pro with M1 Max and 64 GB unified memory

## Packaging Behavior

The app ships with working built-in defaults.

Bundled defaults live in:
- `src/main/resources/systemprompts/`

Those files are compiled into:
- the runnable jar
- the native executable

The native build explicitly includes `systemprompts/**` as classpath resources, so the bundled defaults remain available even when no local override files exist.

At runtime, local filesystem overrides take precedence when present:
- `./systemprompts/application.config`
- `./systemprompts/*.md`
- `./systemprompts/*.yml`
- `./systemprompts/*.json`

So the behavior is:
1. use bundled defaults from the build artifact
2. override them with local files in `./systemprompts/` when they exist
3. keep runtime memory in `./memory/`

## Run Locally

### Recommended

```bash
cd ~/Assistant
mvn -q package
java -jar target/storyteller-1.2.0-all.jar
```

The local default build version is `1.2.0`.
GitHub releases use automatic patch versioning on every push to `main` within the active minor release line, starting with `v1.2.0` and then `v1.2.1`, `v1.2.2`, and so on.
Eligible pushes to `main`, including normal merges from pull requests, automatically build a release jar and publish it to GitHub Releases.
Merges of Dependabot pull requests and pull requests whose source branch is `norelease` or starts with `norelease/` still run CI, but intentionally skip release publication.

### Native Build

```bash
cd ~/Assistant
mvn -Pnative -DskipTests package
```

The native binary is normally written to:

```text
target/storyteller
```

The native build enables GraalVM shared-arena support for JLine's Java FFM terminal provider.

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
java -cp "target/classes:target/dependency/*" nl.llm.storyteller.cli.AssistantApp
```

## Terminal Shortcuts

- `Ctrl-G`: sends `(continue the story)`
- `Ctrl-W`: sends a transient reset instruction that tells the model to strictly follow the active story rules again
- `Ctrl-U`: removes the last persisted turn, sends a transient reset instruction, and restores your last prompt in the input buffer so you can edit and retry it
- `Ctrl-L`: shows the last persisted user prompt and assistant reply without sending anything to the model

On macOS, `Cmd-G`, `Cmd-W`, `Cmd-U`, and `Cmd-L` only work if the terminal forwards those key combinations as meta or escape input.

The `Ctrl-W` / reset turn is treated as a control action rather than as a normal story turn:
- its prompt and response are not appended to `history.json`
- it does not trigger summary, recent-summary, or canonical-state refreshes
- it adds a transient cache-buster token to the first `system` message as a presumed best-effort cache-bust, so stricter or cache-sensitive backends are less likely to reuse the exact same cached prefix for that single request

This is not a documented LM Studio KV-cache flush. It is a portable best-effort prefix break for OpenAI-compatible backends that may reuse internal prompt state when the leading prompt prefix matches exactly.

After every `cacheBuster.interval` persisted story turns, the app also sends this reset-with-cache-buster request internally. Its response is discarded and any failure is ignored, so it never adds a second user-visible message or turns a completed story response into an error. It can add latency. Set the interval to `0` to disable these periodic calls.

The `Ctrl-U` / undo-and-retry action builds on that reset flow:
- it removes the last user+assistant turn from `history.json`
- it clamps the summary, recent-summary, and canonical-state cursors to the shortened history
- it always sends the same transient reset-with-cache-buster request after the removal
- it restores your previous user prompt in the terminal input buffer so you can revise it before sending it again

The `Ctrl-L` action is read-only:
- it does not contact the model
- it does not modify `history.json`
- it simply prints the latest persisted user+assistant turn so you can quickly see where you left off

## Commands

- `/exit` or `/quit`: leave the application
- `/image <instruction>`: read a copied image from the macOS or Windows desktop clipboard and send it once with the instruction to a vision-capable backend; image data is not stored in `history.json`
- `/export`: export the story as Markdown with user prompts in italic
- `/export -intro`: same as `/export`, with user prompts in italic between story sections
- `/export -clean`: export only assistant story output
- `/export -all`: export user prompts and assistant output chronologically with explicit headings
- `/graph`: display the current knowledge graph without calling the model
- `/graph -generate`: create and immediately load a minimal empty graph without calling the model
- `/graph -fill`: generate, validate, persist, and immediately load a graph from the configured fixed protagonists using the model

Exports are written as Markdown files in the application working directory.

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
- `resetcachebuster.md`
- `turnviolationsingletemplate.md`
- `turnviolationpartytemplate.md`
- `graph-predicates.json`

### Optional local overrides

If you create a local `systemprompts/` folder in the working directory with files of the same names, those files override the bundled defaults.

Example override folders are included under [`configs.example/`](configs.example), including mode-specific examples such as [`cowriter_story`](configs.example/cowriter_story) and [`dungeons_dragons`](configs.example/dungeons_dragons).
Those examples show complete prompt sets, fixed protagonists, and runtime defaults for different storytelling modes.

The Dungeons & Dragons example also demonstrates the optional engine-level turn-based game mode.
When enabled, the app itself tracks round participation and can inject penalty instructions into the prompt when a protagonist or the whole party tries to take an extra move before the round is complete.

### Runtime memory

The app reads and writes story memory in `memory/`:
- `history.json`
- `history.md`
- `summary.md`
- `recent-summary.md`
- `canonical-state.yaml`
- `knowledge-graph.json`

These files and their parent `memory/` directory may start out missing. Story history and derived-memory files are created and updated as needed. The graph file is created explicitly through `/graph -generate` or `/graph -fill`, or supplied manually.

### Knowledge graph MVP

Version 1.2.0 introduces a small knowledge graph for mitigation of entity contagion and feature bleeding.
Storyteller loads and validates `memory/knowledge-graph.json` automatically when it exists (configurable through `file.knowledgeGraph`). Changes made while Storyteller is running are picked up automatically before graph facts are used; an invalid intermediate edit never replaces the last valid in-memory snapshot.
When the current user input or candidate response mentions an entity name or alias, its active hard facts are injected into both the story and validation prompts.
Missing files and turns without matching entities preserve the existing behavior.

The graph ontology is closed at runtime but configurable before startup. The bundled catalog supports:

- entity types `CHARACTER`, `ITEM`, `SKILL`, and `LOCATION`;
- bundled predicates for possessions, skills, interpersonal relationships, cohabitation (`LIVES_WITH`), and residence (`LIVES`, `CHARACTER` to `LOCATION`);
- positive and negative active facts, with absent facts resolving to `UNKNOWN`;
- fixed fact sources, statuses, aliases, revision and schema metadata;
- strict entity-reference, predicate-type, duplicate, and contradiction validation;
- immutable in-memory indexes for entity, alias, subject, object, and truth lookup;
- validated, atomically replaced JSON persistence through `KnowledgeGraphStore`;
- reflection-free graph JSON encoding and decoding for GraalVM native images;
- bounded name/alias retrieval and prompt grounding for active hard facts.

Predicate definitions are data-driven through `systemprompts/graph-predicates.json`. A local file with that name replaces the bundled catalog, so it must contain every predicate that should remain available. Each predicate configures its permitted subject and object entity types, temporal flag, and positive and negative prompt text. Facts store the stable predicate ID as a string, so adding a predicate requires no Java enum or formatter change. The same catalog drives graph validation, prompt rendering, and the allowed-predicate instructions for `/graph -fill`.

The bundled relationship predicates are `LOVES`, `TRUSTS`, `HATES`, `PROTECTIVE_OF`, `FRIENDS_WITH`, `TRAINS_WITH`, `FEELS_SAFE_WITH`, and `LIVES_WITH`. `LIVES_WITH` connects two characters; `LIVES` connects a character to a `LOCATION`, such as a villa, penthouse, or house. Relationship facts are directional unless both directions are explicitly present.

Example custom predicate:

```json
{
  "predicates": {
    "MENTORS": {
      "subjectType": "CHARACTER",
      "objectType": "CHARACTER",
      "temporal": false,
      "positiveText": "mentors",
      "negativeText": "does not mentor"
    }
  }
}
```

Facts reference the configured predicate by its string ID. `sourceTurn` is optional for fixed-protagonist facts and is only meaningful when a fact originates from a numbered user or assistant turn:

```json
{
  "id": "valerie-lives-lhorizon",
  "subject": "valerie_thorne",
  "predicate": "LIVES",
  "object": "villa_lhorizon",
  "polarity": "POSITIVE",
  "status": "ACTIVE",
  "source": "FIXED_PROTAGONIST",
  "hard": true
}
```

The CLI provides three graph management commands. They are local control commands and are never recorded as story turns:

- `/graph` displays the current graph and configured JSON path without calling the model;
- `/graph -generate` creates and immediately publishes a minimal empty graph document without calling the model;
- `/graph -fill` sends only the complete configured `fixed_protagonists.yml` content to the model, validates and normalizes the returned closed-ontology graph in Java, atomically replaces the graph file, and immediately publishes the new snapshot.

`/graph -generate` and a successful `/graph -fill` replace the existing graph. `/graph -fill` forces extracted facts to `ACTIVE`, `FIXED_PROTAGONIST`, and `hard=true`; Java controls schema version and revision. Invalid model JSON or a graph validation failure leaves the existing persisted graph and active snapshot unchanged. Normal story turns remain read-only with respect to graph persistence; automatic per-turn mutation remains a later phase documented in [`graph_feature_bleeding_mitigation.md`](docs/architecture/graph_feature_bleeding_mitigation.md).

## Configuration
See: https://github.com/jbrugman/Assistant/wiki/Configuration-&-Hardware-Guide

## Prompt Assembly

The app does not send the full history back to the model. It sends:
- one combined first `system` message containing:
  the main system prompt
  fixed protagonists
  canonical state
  long-term summary
  recent summary
  relevant knowledge-graph facts
- the last `chat.maxRecentTurns` raw turns
- the latest user message

This single-system-message layout improves compatibility with stricter OpenAI-compatible chat templates, including LM Studio model templates that require the system message to appear only at the beginning of the conversation.

The same compatibility rule is also applied to the three background derived-memory updates:
- long-term summary refresh
- recent summary refresh
- canonical state refresh

Those background requests now also use one combined first `system` message followed by one `user` message.

## Memory Layers

The storyteller uses three derived memory layers beside the latest raw turns:
- `memory/recent-summary.md`: recent middle layer
- `memory/summary.md`: long-term background and continuity
- `memory/canonical-state.yaml`: compact confirmed canon

This keeps prompt size down while preserving continuity.

Important runtime detail:
- the foreground story turn stays synchronous for prompt assembly, model response, validation, and history append
- the derived-memory refreshes for `summary.md`, `recent-summary.md`, and `canonical-state.yaml` are triggered asynchronously afterward
- all three refreshes share one single-threaded task queue, so their LLM calls run sequentially without blocking the user from getting the current story response

## Runtime Structure

This remains one Maven project and one distributable jar. Within it, the terminal adapter is deliberately separated from the reusable core:
- `nl.llm.storyteller.cli`: JLine, terminal input, shortcuts, and terminal rendering
- `nl.llm.storyteller.core`, `.core.service`, and `.core.model`: configuration, story behavior, persistence, prompting, and backend integration

This is an intentional modular-monolith choice: deployment, configuration, and operational complexity stay small today, while one-way package dependencies keep the CLI and future HTTP adapter outside the core. If independent deployment becomes useful later, those adapters can move into separate modules or services without moving storyteller behavior out of the core first.

The runtime responsibilities are now split more explicitly:
- [`AssistantApp.java`](src/main/java/nl/llm/storyteller/cli/AssistantApp.java): minimal CLI entrypoint and resource lifecycle
- [`ApplicationFactory.java`](src/main/java/nl/llm/storyteller/core/ApplicationFactory.java): assembles the reusable core dependency graph
- [`OpenAiCompatibleHttpClient.java`](src/main/java/nl/llm/storyteller/core/service/OpenAiCompatibleHttpClient.java): shared chat-completions adapter for LM Studio, Ollama, hosted APIs, llama-server, and mlx-vlm
- [`ManagedLlamaServer.java`](src/main/java/nl/llm/storyteller/core/service/ManagedLlamaServer.java): optional local llama-server process lifecycle and readiness handling
- [`ManagedMlxServer.java`](src/main/java/nl/llm/storyteller/core/service/ManagedMlxServer.java): optional local mlx-vlm process lifecycle and readiness handling
- [`TerminalStoryteller.java`](src/main/java/nl/llm/storyteller/cli/TerminalStoryteller.java): JLine input loop, shortcuts, command handling, and UI error policy
- [`TerminalRenderer.java`](src/main/java/nl/llm/storyteller/cli/TerminalRenderer.java): terminal formatting, wrapping, banners, and user-visible messages
- [`StorySessionService.java`](src/main/java/nl/llm/storyteller/core/service/StorySessionService.java): prompt assembly, model call, validation, history append, and derived-memory refresh triggering
- [`PromptAssemblyService.java`](src/main/java/nl/llm/storyteller/core/service/PromptAssemblyService.java): coordinates prompt building from prompts, memory, and recent turns

Prompt responsibilities are now split more explicitly:
- [`PromptResourceLoader.java`](src/main/java/nl/llm/storyteller/core/service/PromptResourceLoader.java): loads raw prompt resources
- [`PromptTemplateService.java`](src/main/java/nl/llm/storyteller/core/service/PromptTemplateService.java): formats reusable prompt fragments
- [`StoryChatPromptBuilder.java`](src/main/java/nl/llm/storyteller/core/service/StoryChatPromptBuilder.java): builds the main storyteller chat stack
- [`ValidationPromptBuilder.java`](src/main/java/nl/llm/storyteller/core/service/ValidationPromptBuilder.java): builds validator system and user payloads
- [`SummaryPromptBuilder.java`](src/main/java/nl/llm/storyteller/core/service/SummaryPromptBuilder.java), [`RecentSummaryPromptBuilder.java`](src/main/java/nl/llm/storyteller/core/service/RecentSummaryPromptBuilder.java), and [`CanonicalStatePromptBuilder.java`](src/main/java/nl/llm/storyteller/core/service/CanonicalStatePromptBuilder.java): build the three derived-memory update prompts

Those builders now take small prompt-input records from [`src/main/java/nl/llm/storyteller/core/model`](src/main/java/nl/llm/storyteller/core/model), so prompt inputs are explicit instead of being passed around as long ordered `String` argument lists.

Configuration follows the same separation:
- [`AppConfigLoader.java`](src/main/java/nl/llm/storyteller/core/config/AppConfigLoader.java) and [`AppConfigSource.java`](src/main/java/nl/llm/storyteller/core/config/AppConfigSource.java): loading, merging, and path resolution
- [`AppConfig.java`](src/main/java/nl/llm/storyteller/core/config/AppConfig.java): validated runtime settings only

Graph responsibilities are separated as well:
- [`PredicateCatalog.java`](src/main/java/nl/llm/storyteller/core/graph/PredicateCatalog.java): immutable, configuration-driven predicate definitions
- [`KnowledgeGraphValidator.java`](src/main/java/nl/llm/storyteller/core/graph/KnowledgeGraphValidator.java): entity, predicate-type, reference, duplicate, and contradiction validation
- [`ReadOnlyKnowledgeGraphService.java`](src/main/java/nl/llm/storyteller/core/graph/ReadOnlyKnowledgeGraphService.java): automatic snapshot refresh, entity resolution, and bounded fact rendering
- [`KnowledgeGraphStore.java`](src/main/java/nl/llm/storyteller/core/graph/persistence/KnowledgeGraphStore.java): atomic graph persistence
- [`KnowledgeGraphJsonCodec.java`](src/main/java/nl/llm/storyteller/core/graph/persistence/KnowledgeGraphJsonCodec.java): reflection-free JSON I/O for JVM and native-image builds

The runtime flow is documented as one full diagram and four focused diagrams:
- [full storyteller flow](docs/architecture/02-storytelller-flow-design-full.puml)
- [runtime flow](docs/architecture/02-a-storytelller-flow-design-runtime.puml)
- [knowledge-graph flow](docs/architecture/02-b-storytelller-flow-design-graph.puml)
- [image flow](docs/architecture/02-c-storytelller-flow-design-image.puml)
- [story-session flow](docs/architecture/02-d-storytelller-flow-design-storysession.puml)

Validation is also split into focused parts:
- [`ValidationClient.java`](src/main/java/nl/llm/storyteller/core/service/ValidationClient.java): sends the validator prompt to the configured model
- [`ValidationDecisionParser.java`](src/main/java/nl/llm/storyteller/core/service/ValidationDecisionParser.java): extracts `ALLOW` or `REPLACE` from structured or plain-text validator output
- [`ValidationOutcome.java`](src/main/java/nl/llm/storyteller/core/model/ValidationOutcome.java): compact validation decision model with small decision helpers
- [`ResponseSanitizer.java`](src/main/java/nl/llm/storyteller/core/service/ResponseSanitizer.java): cleans visible JSON-style escapes before terminal output
- [`ResponseGuard.java`](src/main/java/nl/llm/storyteller/core/service/ResponseGuard.java): coordinates those parts, including validator-provided replacement text when a rewrite is needed

LLM backend resilience is handled separately:
- [`ResilientChatClient.java`](src/main/java/nl/llm/storyteller/core/service/ResilientChatClient.java): wraps a `ChatClient` with fail-fast cooldown behavior
- [`LlmBackendGuard.java`](src/main/java/nl/llm/storyteller/core/service/LlmBackendGuard.java): tracks repeated failures and temporarily opens a cooldown window after the configured threshold

The three derived-memory updaters now share one common infrastructure layer:
- [`DerivedMemoryTaskQueue.java`](src/main/java/nl/llm/storyteller/core/service/DerivedMemoryTaskQueue.java): shared sequential execution and worker lifecycle
- [`DerivedMemoryManager.java`](src/main/java/nl/llm/storyteller/core/service/DerivedMemoryManager.java): per-manager concurrency guard, model-call flow, and safe write-back coordination
- [`SummaryManager.java`](src/main/java/nl/llm/storyteller/core/service/SummaryManager.java), [`RecentSummaryManager.java`](src/main/java/nl/llm/storyteller/core/service/RecentSummaryManager.java), and [`CanonicalStateManager.java`](src/main/java/nl/llm/storyteller/core/service/CanonicalStateManager.java): their own cutoff rules and prompt contents

Those background memory refreshes are asynchronous by design:
- `StorySessionService` triggers them after the current turn has already been appended to history
- all managers submit to one daemon-backed single-thread queue, preventing concurrent background calls to the LLM backend
- if a background refresh fails, the current user-facing turn still completes normally

## Example usage

The most effective way to use the app is to steer the story with compact, concrete scene instructions. The system prompt, fixed protagonists, canonical state, and validator then help keep the output aligned with the world and character constraints you defined.

```text
  > It is Saturday morning. Mark wakes up after another restless night. The past week was filled with difficult customer complaints. He feels mentally exhausted. Write the next scene from Mark's perspective. His mild headache subtly influences his observations, thoughts and reactions.
```

or:

```text
  > It is Saturday morning. Mark wakes up after another restless night. The past week has been mentally exhausting. The scene is experienced entirely from Mark's perspective. His headache should naturally influence his mood and concentration.
```

That kind of prompt might return something like:

```text
The light behind the curtains is thin, but it still finds me. It presses straight through my eyelids and into the dull ache behind my forehead. For a moment I stay flat on my back, one arm over my eyes, hoping the headache will settle if I refuse to move.

It doesn't.

My phone is on the nightstand. I don't need to pick it up to know what's waiting there. More messages. More follow-up questions. More people treating a complicated system like a vending machine that failed to drop the right snack. Even thinking about the inbox makes my jaw tighten.

I turn my head toward the window too quickly and regret it at once. The room tilts for half a second, just enough to make me close my eyes again. Great. Excellent start.

Somewhere deeper in the house, a door closes softly. Pipes murmur in the walls. The estate is awake in that careful, muted way it always is, as if the building has agreed never to embarrass itself before noon. Normally I like that. Normally it feels controlled. This morning it just makes me aware of how noisy my own head is.

I sit up slowly. The edge of the bed is cold against my legs. My laptop is still open on the desk where I left it, dark screen tilted toward the chair, charger trailing across the floor like an accusation. I try to remember whether I actually solved anything yesterday or just kept moving tasks from one column to another until midnight felt like progress.

The answer comes with another pulse behind my eyes.

Not yet.
```

## Changelog

### 1.2.0
- Added a validated knowledge graph that grounds story generation and validation with relevant hard facts to reduce entity, skill, relationship, and location bleeding.
- Added a configuration-driven predicate catalog with directional relationships, `LOCATION` support, automatic loading, atomic persistence, and native-image-compatible JSON handling.
- Added local `/graph` inspection, model-free `/graph -generate`, and model-assisted `/graph -fill` using only the configured fixed protagonists.
- Added graph regression coverage and updated the architecture documentation with focused runtime, graph, image, and story-session flows.

Read more: https://github.com/jbrugman/Assistant/wiki/Changelog
