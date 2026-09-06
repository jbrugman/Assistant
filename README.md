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

## Benchmarking

The app includes a fixed 50-turn benchmark that measures both long-term fact retention and validation of binding
fixed-protagonist constraints. It can be repeated with different models, quantizations, and feature combinations.
Specify a model after `/benchmark`, or omit it to use the model already loaded by the backend. The validation,
cache-buster, and knowledge-graph switches can be enabled independently so their effects can be compared.

The initial results below include the official Gemma 4 26B A4B model available through LM Studio using its 6-bit MLX
variant, and Gemma 4 12B QAT running on an RTX 3080 Ti with speculative decoding enabled. These results are preliminary
observations from individual runs rather than definitive performance claims.

| Model and runtime | Validation | Cache-buster | Knowledge graph | Facts retained | Validation probes | Total time |
|:---|:---:|:---:|:---:|---:|---:|---:|
| Gemma 4 26B A4B, MLX 6-bit | Off | Off | Off | 20% (1/5) | — | 1m 09s |
| Gemma 4 26B A4B, MLX 6-bit | Off | Off | On | 100% (5/5) | — | 3m 19s |
| Gemma 4 26B A4B, MLX 6-bit | Off | On | Off | 20% (1/5) | — | 1m 48s |
| Gemma 4 26B A4B, MLX 6-bit | On | Off | On | 100% (5/5) | 4/4 | 4m 30s |
| Gemma 4 12B QAT, RTX 3080 Ti with speculative decoding | Off | Off | Off | 0% (0/5) | — | 0m 18s |
| Gemma 4 12B QAT, RTX 3080 Ti with speculative decoding | Off | Off | On | 100% (5/5) | — | 1m 16s |
| Gemma 4 12B QAT, RTX 3080 Ti with speculative decoding | Off | On | Off | 0% (0/5) | — | 0m 24s |
| Gemma 4 12B QAT, RTX 3080 Ti with speculative decoding | On | Off | Off | 0% (0/5) | 2/4 | 0m 52s |
| Gemma 4 12B QAT, RTX 3080 Ti with speculative decoding | On | Off | On | 100% (5/5) | 4/4 | 1m 44s |

In this fixed scenario, knowledge-graph injection was associated with substantially better fact retention. Retention
increased from 20% to 100% for Gemma 4 26B A4B and from 0% to 100% for Gemma 4 12B QAT. These individual runs do not
establish that the same improvement will occur with other models, stories, context sizes, hardware, or repeated runs.

Validation checks explicit rules and fixed-protagonist constraints; it does not recover historical facts that are no
longer present in the model context. It therefore did not improve fact retention when the knowledge graph was disabled.
With knowledge-graph injection enabled, validation corrected all four deliberate constraint violations for both models.
In the separate Gemma 4 12B run without knowledge-graph injection, only two of four validation probes produced usable
corrections. More repeated tests are required before attributing that difference to the knowledge-graph context.

The matched cache-buster comparisons showed no quality improvement. For Gemma 4 26B A4B, retention remained at 20%
while total time increased from 1m 09s to 1m 48s. For Gemma 4 12B QAT, retention remained at 0% while total time
increased from 18 to 24 seconds. This evidence is limited to two models and one fixed scenario, so cache-busting remains
available pending broader testing. The provisional recommendation is to disable it with `cacheBuster.enabled=false`.

The Gemma 4 12B QAT runs on the RTX 3080 Ti were also considerably faster than the Gemma 4 26B A4B MLX runs on the
Mac. For example, the configuration with validation disabled, cache-busting disabled, and knowledge-graph injection
enabled completed in 1m 16s on the RTX 3080 Ti versus 3m 19s on the Mac. This is not a direct hardware benchmark:
the model sizes, quantizations, inference runtimes, and use of speculative decoding differ. It does show that the tested
12B setup on the RTX 3080 Ti delivered substantially shorter benchmark times in practice.

The benchmark reports total wall-clock time and average turn time, but not model generation speed. The generic
OpenAI-compatible response does not provide enough portable timing information to distinguish prompt evaluation,
time to first token, token generation, and request overhead reliably. Use the backend's own generation timing for a
hardware tok/s measurement.

## Recommended setup
See: https://github.com/jbrugman/Assistant/wiki/Configuration-&-Hardware-Guide

Hosted OpenAI-compatible endpoints can use an optional bearer API key in `application.config`:

```properties
backend.type=openai-compatible
backend.http.url=https://example.test/v1/chat/completions
backend.http.apiKey=your-api-key
```

Leave `backend.http.apiKey` blank for local endpoints that do not require authentication. The key is sent as `Authorization: Bearer <key>` and applies to chat, validation, and background-memory requests. Because the configuration value is stored as plain text, keep local configuration files containing real keys out of version control.

For an oMLX server using its default port and required API-key verification, use:

```properties
backend.type=openai-compatible
backend.http.url=http://localhost:8000/v1/chat/completions
backend.http.apiKey=your-omlx-api-key
```

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
- `storyteller-core/src/main/resources/systemprompts/`

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
java -jar storyteller-cli/target/storyteller-cli-1.3.3-all.jar
```

The CLI jar does not contain Javalin, Jetty, H2, or the API implementation. Run the independent API application with:

```bash
java -jar storyteller-api/target/storyteller-api-1.3.3-all.jar
```

Alternatively, start the API directly through Maven from the project root:

```bash
mvn -pl storyteller-api -am exec:java \
  -Dexec.mainClass=nl.llm.storyteller.api.ApiApplication
```

The CLI and API are separate applications with separate entry points. Both depend on `storyteller-core`; neither application contains the other. The API module also owns its own `application.config`.

Screenshots of the server-rendered web interface are available in [`docs/webpages`](docs/webpages/).
The web interface can disable inactivity expiration for the active story with **Infinite** and restore the configured
session timeout with **Use timeout**.
The start page can import a CLI-compatible session ZIP containing `history.json` and any available summary,
canonical-state, turn-state, and knowledge-graph files. **Export** downloads the active web session in the same portable
format, including a versioned manifest that preserves the story title.

#### Session ZIP import and export

Use `/export -zip` in the CLI to create a portable `story-session-<timestamp>.zip`. The same format can also be
downloaded from an active story in the web interface by selecting **Export**. The archive contains:

- `manifest.json` with the bundle format version and story title
- `history.json` with all user and assistant messages and the three memory cursors
- `turn-state.json`
- `knowledge-graph.json`
- `summary.md`, `recent-summary.md`, and `canonical-state.yaml` when those values exist

To restore it, stop or leave the current web session, select the ZIP under **Import CLI session (ZIP)** on the start
page, and submit the form. Import validates the complete archive, creates a new database session with a new ID, and
then opens that session. It does not overwrite an existing session. The other CLI `/export` modes continue to produce
Markdown files.

After creating a session, submit a story prompt through `POST /v1/sessions/{sessionId}/turns` with a JSON body such as
`{"prompt":"Continue into the forest."}`. The response contains the generated story text and the persisted user and
assistant message indices. Session-specific prompt inspection and overrides are not exposed yet.

The local default build version is `1.3.3`.
GitHub releases use automatic patch versioning on every push to `main` within the active minor release line, starting with `v1.3.0` and then `v1.3.1`, `v1.3.2`, and so on.
Eligible pushes to `main`, including normal merges from pull requests, automatically build a release jar and publish it to GitHub Releases.
Merges of Dependabot pull requests and pull requests whose source branch is `norelease` or starts with `norelease/` still run CI, but intentionally skip release publication.

### Native Build

```bash
cd ~/Assistant
mvn -pl storyteller-cli -am -Pnative -DskipTests package
```

The native binary is normally written to:

```text
storyteller-cli/target/storyteller
```

The native build enables GraalVM shared-arena support for JLine's Java FFM terminal provider.

Build the independent API native executable with:

```bash
mvn -pl storyteller-api -am -Pnative-api -DskipTests package
```

That executable is written to `storyteller-api/target/storyteller-api` and starts the HTTP server automatically.

Run it from the project root:

```bash
cd ~/Assistant
./storyteller-cli/target/storyteller
```

If an `application.config` file exists next to the native executable, it is loaded as an additional runtime override.

### Development Run

```bash
cd ~/Assistant
mvn -q -pl storyteller-cli -am package
java -jar storyteller-cli/target/storyteller-cli-1.3.3-all.jar
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

Set `cacheBuster.enabled=false` to disable cache-buster processing independently of its configured interval. Set `graph.enabled=false` to disable both graph injection and automatic turn-based graph extraction. These are the same core switches used by the benchmark; `validation.enabled` controls validation in the same way.

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
- `/graph -reset`: remove only entities and facts whose source is `TURNBASED`
- `/benchmark [-<model>]`: run the fixed, isolated 50-turn retention benchmark with the requested model, or the model already loaded by the backend when omitted

The benchmark never reads or changes the normal story history or knowledge graph. Its defaults are fixed at seed `42`, temperature `0`, top-k `1`, top-p `1`, two recent turns, at most 128 generated tokens for story and validation requests, and at most 2048 tokens for graph JSON extraction. The English-only scenario tests `LIVES`, `TRUSTS`, and changing `WEARS` facts supported by the graph ontology. When supplied, the model name after `/benchmark` is sent as the chat and validator model. Without it, no model override is sent and the backend uses its already loaded model; `model.chat` from `application.config` is not substituted. A managed MLX benchmark reuses the already running server, so an explicitly selected model must already be the configured managed MLX model. Start it with `--max-kv-size 4096` and a server output ceiling of at least `--max-tokens 2048`; individual story requests remain capped at 128 tokens. An OpenAI-compatible remote endpoint controls its own context and server output limits.

Every run writes an English audit report under `benchmark-results/`. It records each probe, the original draft, the final response after the normal validator, expected or forbidden assertions, pass/fail status, whether validation replaced the draft, and graph revision/entity/fact counts. Fact-retention probes remain separate from adversarial validation probes. The latter use a binding fixed-protagonist constraint that Alice is romantically and sexually attracted only to women, plus an explicit rule that fixed-protagonist world state must be preserved, and then attempt to introduce attraction toward named men. The real validator receives those normal rules and fixed-protagonist data. The summary counts replacements across all turns; improvements and regressions count replacements on scored probes where correctness can be determined. A validation-probe `REPLACE` counts as useful when it retains Alice and removes the forbidden assertion; it does not need to retain the named man from that forbidden assertion. Rejected graph updates are also reported instead of silently presenting an empty graph as a model retention failure.

`Graph updates rejected` counts candidate graph updates that Java refused to persist. A rejection is atomic: the complete candidate update is discarded and the previously stored graph remains unchanged. Typical reasons include invalid JSON, missing required fields, invalid identifiers, unknown entity references, type-incompatible relationships, contradictions, and predicates that are not present in the configured predicate catalog. For example, when a model emits an unsupported `DESIRE` predicate, that predicate is never added to the graph; the rejected-update counter and audit reason record the blocked attempt.

Each feature can be measured independently:

```text
/benchmark --validation=off --cache-buster=on --knowledge-graph=on
```

```text
/benchmark -google/gemma-4-26b-a4b --validation=off --cache-buster=on --knowledge-graph=on
```


Use `--turns=10` through `--turns=100` to change the fixed run length. All three feature switches default to `on`. Run the command repeatedly with one switch changed at a time for comparable results.

Exports are written as Markdown files in the application working directory.

## Runtime Files

### Bundled defaults

These are compiled into the app from `storyteller-core/src/main/resources/systemprompts/`:
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

These files and their parent `memory/` directory may start out missing. Story history and derived-memory files are created and updated as needed. The graph file is created through `/graph -generate`, `/graph -fill`, a configured turn-based update, or supplied manually.

### Knowledge graph MVP

Version 1.2.0 introduces a small knowledge graph for mitigation of entity contagion and feature bleeding.
Storyteller loads and validates `memory/knowledge-graph.json` automatically when it exists (configurable through `file.knowledgeGraph`). Changes made while Storyteller is running are picked up automatically before graph facts are used; an invalid intermediate edit never replaces the last valid in-memory snapshot.
When the current user input or candidate response mentions an entity name or alias, its active facts are injected into both the story and validation prompts. Hard manual and fixed-protagonist facts are labeled authoritative. Model-generated turn-based facts are labeled as lower-confidence context and may never override authoritative facts.
Missing files and turns without matching entities preserve the existing behavior.

The graph ontology is closed at runtime but configurable before startup. The bundled catalog supports:

- entity types `CHARACTER`, `ITEM`, `SKILL`, and `LOCATION`;
- bundled predicates for possessions, worn clothing (`WEARS`), skills, interpersonal relationships, cohabitation (`LIVES_WITH`), and residence (`LIVES`, `CHARACTER` to `LOCATION`);
- positive and negative active facts, with absent facts resolving to `UNKNOWN`;
- source metadata on both entities and facts, plus statuses, aliases, revision and schema metadata;
- strict entity-reference, predicate-type, duplicate, and contradiction validation;
- immutable in-memory indexes for entity, alias, subject, object, and truth lookup;
- validated, atomically replaced JSON persistence through `KnowledgeGraphStore`;
- reflection-free graph JSON encoding and decoding for GraalVM native images;
- bounded name/alias retrieval and prompt grounding for active hard facts.

Predicate definitions are data-driven through `systemprompts/graph-predicates.json`. A local file with that name replaces the bundled catalog, so it must contain every predicate that should remain available. Each predicate configures its permitted subject and object entity types, temporal flag, and positive and negative prompt text. Facts store the stable predicate ID as a string, so adding a predicate requires no Java enum or formatter change. The same catalog drives graph validation, prompt rendering, and the allowed-predicate instructions for `/graph -fill`.

The bundled relationship predicates are `LOVES`, `TRUSTS`, `HATES`, `PROTECTIVE_OF`, `FRIENDS_WITH`, `TRAINS_WITH`, `FEELS_SAFE_WITH`, and `LIVES_WITH`. `LIVES_WITH` connects two characters; `LIVES` connects a character to a `LOCATION`, such as a villa, penthouse, or house. Relationship facts are directional unless both directions are explicitly present.

`WEARS` connects a `CHARACTER` to an `ITEM` and is temporal. Multiple garments are represented as separate item entities and facts, rather than an array-valued fact. For example, a blue pair of jeans and a shirt produce two `ITEM` entities and two `WEARS` facts. On a clothing change, automatic extraction returns the character's complete resulting outfit. That snapshot replaces only the character's previous `TURNBASED` `WEARS` facts; manual and fixed-protagonist clothing remains protected. Automatically generated garment entities are removed when the replacement leaves them unreferenced.

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

Every entity also has a `source`. Fixed-protagonist generation assigns `FIXED_PROTAGONIST`; automatic extraction from completed story turns assigns `TURNBASED` to every generated entity and fact. TURNBASED facts are always normalized to `hard=false` and therefore carry less weight than hard manual or fixed-protagonist facts.

Automatic graph extraction runs after every `graph.turnBased.batchTurns` completed turns. The default is `3`. Only that latest batch of complete user-assistant turns is sent for extraction, and the resulting candidate is merged atomically. Existing non-TURNBASED entities and facts are protected from replacement or contradiction.

Turn-based relationship extraction is deliberately conservative. An interaction such as talking, flirting, kissing, sex, cooperation, shared time, or momentary affection is not sufficient evidence for an enduring relationship such as `FRIENDS_WITH`, `LOVES`, or `TRUSTS`. The completed turns must explicitly establish the relationship; otherwise the extractor omits it.

The CLI provides four graph management commands. They are local control commands and are never recorded as story turns:

- `/graph` displays the current graph and configured JSON path without calling the model;
- `/graph -generate` creates and immediately publishes a minimal empty graph document without calling the model;
- `/graph -fill` sends only the complete configured `fixed_protagonists.yml` content to the model, validates and normalizes the returned closed-ontology graph in Java, atomically replaces the graph file, and immediately publishes the new snapshot;
- `/graph -reset` atomically removes only TURNBASED entities and facts while preserving fixed-protagonist, manual, and other sourced data.

`/graph -generate` and a successful `/graph -fill` replace the existing graph. `/graph -fill` forces extracted entities and facts to `FIXED_PROTAGONIST`, with facts set to `ACTIVE` and `hard=true`; Java controls schema version and revision. Invalid model JSON or a graph validation failure leaves the existing persisted graph and active snapshot unchanged. Fill generation and automatic turn-based extraction use the same sequential derived-memory queue, preventing either operation from overwriting a graph update produced concurrently by the other. Because `/graph -fill` waits for its queued result, earlier summary, canonical-state, or graph work may delay the command. Automatic turn-based extraction is best-effort and never fails the completed foreground story turn; a candidate based on a stale graph revision is skipped and is not reported as successfully persisted.

## Configuration
See: https://github.com/jbrugman/Assistant/wiki/Configuration-&-Hardware-Guide

Set `graph.turnBased.batchTurns` to the number of completed story turns per automatic graph update. It must be at least `1` and defaults to `3`.

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
- the three memory refreshes, automatic turn-based graph extraction, and manual `/graph -fill` generation share one single-threaded task queue, so their LLM calls run sequentially; background work does not block the current story response, while `/graph -fill` deliberately waits for earlier queued work and its own result

## Runtime Structure

This remains one Maven project and one distributable jar. Within it, the terminal adapter is deliberately separated from the reusable core:
- `nl.llm.storyteller.cli`: JLine, terminal input, shortcuts, and terminal rendering
- `nl.llm.storyteller.core`, `.core.service`, and `.core.model`: configuration, story behavior, persistence, prompting, and backend integration
- `nl.llm.storyteller.core.graph.service`: graph query, initialization, fixed-protagonist generation, and reset services
- `nl.llm.storyteller.core.graph.turnbasedservice`: periodic lower-authority graph extraction and merge from completed turns

This is an intentional modular-monolith choice: deployment, configuration, and operational complexity stay small today, while one-way package dependencies keep the CLI and future HTTP adapter outside the core. If independent deployment becomes useful later, those adapters can move into separate modules or services without moving storyteller behavior out of the core first.

The runtime responsibilities are now split more explicitly:
- [`AssistantApp.java`](storyteller-cli/src/main/java/nl/llm/storyteller/cli/AssistantApp.java): minimal CLI entrypoint and resource lifecycle
- [`ApplicationFactory.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/ApplicationFactory.java): assembles the reusable core dependency graph
- [`OpenAiCompatibleHttpClient.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/OpenAiCompatibleHttpClient.java): shared chat-completions adapter for LM Studio, Ollama, hosted APIs, llama-server, and mlx-vlm
- [`ManagedLlamaServer.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/ManagedLlamaServer.java): optional local llama-server process lifecycle and readiness handling
- [`ManagedMlxServer.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/ManagedMlxServer.java): optional local mlx-vlm process lifecycle and readiness handling
- [`TerminalStoryteller.java`](storyteller-cli/src/main/java/nl/llm/storyteller/cli/TerminalStoryteller.java): JLine input loop, shortcuts, command handling, and UI error policy
- [`TerminalRenderer.java`](storyteller-cli/src/main/java/nl/llm/storyteller/cli/TerminalRenderer.java): terminal formatting, wrapping, banners, and user-visible messages
- [`StorySessionService.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/StorySessionService.java): prompt assembly, model call, validation, history append, and derived-memory refresh triggering
- [`PromptAssemblyService.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/PromptAssemblyService.java): coordinates prompt building from prompts, memory, and recent turns

Prompt responsibilities are now split more explicitly:
- [`PromptResourceLoader.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/PromptResourceLoader.java): loads raw prompt resources
- [`PromptTemplateService.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/PromptTemplateService.java): formats reusable prompt fragments
- [`StoryChatPromptBuilder.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/StoryChatPromptBuilder.java): builds the main storyteller chat stack
- [`ValidationPromptBuilder.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/ValidationPromptBuilder.java): builds validator system and user payloads
- [`SummaryPromptBuilder.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/SummaryPromptBuilder.java), [`RecentSummaryPromptBuilder.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/RecentSummaryPromptBuilder.java), and [`CanonicalStatePromptBuilder.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/CanonicalStatePromptBuilder.java): build the three derived-memory update prompts

Those builders now take small prompt-input records from [`storyteller-core/src/main/java/nl/llm/storyteller/core/model`](storyteller-core/src/main/java/nl/llm/storyteller/core/model), so prompt inputs are explicit instead of being passed around as long ordered `String` argument lists.

Configuration follows the same separation:
- [`AppConfigLoader.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/config/AppConfigLoader.java) and [`AppConfigSource.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/config/AppConfigSource.java): loading, merging, and path resolution
- [`AppConfig.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/config/AppConfig.java): validated runtime settings only

Graph responsibilities are separated as well:
- [`PredicateCatalog.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/graph/PredicateCatalog.java): immutable, configuration-driven predicate definitions
- [`KnowledgeGraphValidator.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/graph/KnowledgeGraphValidator.java): entity, predicate-type, reference, duplicate, and contradiction validation
- [`ReadOnlyKnowledgeGraphService.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/graph/service/ReadOnlyKnowledgeGraphService.java): automatic snapshot refresh, entity resolution, and authority-aware fact rendering
- [`KnowledgeGraphManagementService.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/graph/service/KnowledgeGraphManagementService.java): selective TURNBASED reset
- [`TurnBasedKnowledgeGraphService.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/graph/turnbasedservice/TurnBasedKnowledgeGraphService.java): configured turn batching, extraction, source normalization, and protected merge
- [`KnowledgeGraphStore.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/graph/persistence/KnowledgeGraphStore.java): atomic graph persistence
- [`KnowledgeGraphJsonCodec.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/graph/persistence/KnowledgeGraphJsonCodec.java): reflection-free JSON I/O for JVM and native-image builds

The runtime flow is documented as one full diagram and four focused diagrams:
- [full storyteller flow](docs/architecture/02-storytelller-flow-design-full.puml)
- [runtime flow](docs/architecture/02-a-storytelller-flow-design-runtime.puml)
- [knowledge-graph flow](docs/architecture/02-b-storytelller-flow-design-graph.puml)
- [image flow](docs/architecture/02-c-storytelller-flow-design-image.puml)
- [story-session flow](docs/architecture/02-d-storytelller-flow-design-storysession.puml)

Validation is also split into focused parts:
- [`ValidationClient.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/ValidationClient.java): sends the validator prompt to the configured model
- [`ValidationDecisionParser.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/ValidationDecisionParser.java): extracts `ALLOW` or `REPLACE` from structured or plain-text validator output
- [`ValidationOutcome.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/model/ValidationOutcome.java): compact validation decision model with small decision helpers
- [`ResponseSanitizer.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/ResponseSanitizer.java): cleans visible JSON-style escapes before terminal output
- [`ResponseGuard.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/ResponseGuard.java): coordinates those parts, including validator-provided replacement text when a rewrite is needed

LLM backend resilience is handled separately:
- [`ResilientChatClient.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/ResilientChatClient.java): wraps a `ChatClient` with fail-fast cooldown behavior
- [`LlmBackendGuard.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/LlmBackendGuard.java): tracks repeated failures and temporarily opens a cooldown window after the configured threshold

The three derived-memory updaters now share one common infrastructure layer:
- [`DerivedMemoryTaskQueue.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/DerivedMemoryTaskQueue.java): shared sequential execution and worker lifecycle
- [`DerivedMemoryManager.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/DerivedMemoryManager.java): per-manager concurrency guard, model-call flow, and safe write-back coordination
- [`SummaryManager.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/SummaryManager.java), [`RecentSummaryManager.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/RecentSummaryManager.java), and [`CanonicalStateManager.java`](storyteller-core/src/main/java/nl/llm/storyteller/core/service/CanonicalStateManager.java): their own cutoff rules and prompt contents

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

### 1.3.5
- Added an optional infinite-session mode that keeps a story out of inactivity cleanup until the normal timeout is restored.
- Added an Undo control that atomically removes the latest prompt and response from the active web story.
- Added `/export -zip` to the CLI and transactional session ZIP import/export to the web application for transferring history and derived memory between them.

### 1.3.4
- Added a responsive server-rendered web interface for starting, continuing, and permanently stopping story sessions on mobile, tablet, and desktop.
- Added adjustable single/dual-column and normal/maximized reading layouts, automatic positioning at the latest exchange, and duplicate-submit protection while a response is being generated.

### 1.3.3
- Improved benchmark validation so rule violations are replaced with corrected story text, and expanded the documented benchmark comparison across validation, cache-buster, and knowledge-graph configurations.
- Added the first story interaction endpoint to the API, with session-owned database history and atomic persistence of completed user and assistant turns.

### 1.3.2
- Prevented `/graph -fill` and automatic turn-based knowledge-graph extraction from overwriting each other's updates.

### 1.3.1
- Added an isolated, reproducible local-model benchmark with a fixed fact-retention scenario and independently switchable validation, cache-buster, and knowledge-graph processing.

### 1.3.0
- Reorganized the project into separate `storyteller-core`, `storyteller-cli`, and `storyteller-api` Maven modules. The CLI and new API are independent applications that share the core, keeping their code and dependencies out of each other's distributions.
- The API currently supports only creating a session and resuming the active session through a secure session cookie; story interaction is not available through the API yet.

### 1.2.3
- Added optional `backend.http.apiKey` configuration for bearer authentication with hosted OpenAI-compatible endpoints.
- Made turn-based relationship extraction conservative so interactions such as talking, flirting, kissing, cooperation, or momentary affection do not automatically become enduring relationship facts.
- Added the temporal `WEARS` predicate for clothing, represented as separate `ITEM` entities and facts per garment.
- Added outfit snapshot replacement so a character's obsolete `TURNBASED` clothing is removed after changing clothes, while manual and fixed-protagonist graph data remains protected.

### 1.2.2
- Added configurable automatic knowledge-graph updates from every `graph.turnBased.batchTurns` completed story turns, defaulting to three.
- Added source metadata to graph entities and introduced lower-authority `TURNBASED` entities and facts that cannot override fixed-protagonist or manual graph data.
- Added `/graph -reset` to remove only TURNBASED graph items, including startup help, CLI validation, regression tests, and updated architecture diagrams.
- Moved graph services into `core.graph.service` and isolated automatic turn extraction and merge in `core.graph.turnbasedservice`.

Read more: https://github.com/jbrugman/Assistant/wiki/Changelog
