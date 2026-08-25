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

## Recommended Setup

- Recommended reference machine: MacBook Pro with M1 Max and 64 GB unified memory
- Better results usually come from a relatively large mature instruct model, such as `google/gemma-4-26b-a4b-it-qat` in a 6-bit MLX quantization with reasoning disabled, using roughly 21.8 GB and a 32K to 48K context window
- Minimum recommended class: Gemma 4 12B QAT or a roughly comparable model
- If you have less memory available, the default Gemma 4 26B A4B 4-bit variant is roughly `16 GB`, which makes it practical to experiment with a `32K` context window on a machine such as an M1 Mac with `32 GB` of unified memory

## Workable Setup

- MacBook Pro with Apple Silicon (`M`-series) and `32 GB` to `48 GB` of unified memory
- [google/gemma-4-12b-qat](https://lmstudio.ai/models/google/gemma-4-12b-qat), preferably the 4-bit variant, using roughly `8 GB` of model memory
- A `32K` context window, using roughly another `8 GB` of memory

The 12B dense model occasionally makes stylistic mistakes, but it is generally good enough to follow story rules and hard constraints.
It is a practical model for experimenting with the storyteller app on a smaller machine.
Including runtime, context, and general system overhead, total memory usage will usually end up around `18 GB` to `20 GB`, which is still workable on a `32 GB` unified memory machine.
The broader takeaway is that Apple Silicon with a moderate amount of unified memory is already a very capable platform for exploring local large language models.

## Hardware Fit At A Glance

This table is a practical fit guide for local storyteller use with quantized models.
The context ranges in the hardware columns refer to the model context window size used for this app (recommended values).
It is meant as a quick "is this worth trying on my machine?" reference, not as a benchmark table or a hard compatibility guarantee.

| Model                     | Parameters | RTX 3080 Ti 12 GB (`16K`) | RTX 4090 24 GB (`16K` to `32K`) | Mac 32 GB unified (`16K` to `32K`) | Mac 64 GB unified (`32K` to `48K`) | Practical take                                                                          |
|---------------------------|-----------:|---------------------------|---------------------------------|------------------------------------|------------------------------------|-----------------------------------------------------------------------------------------|
| `Qwen3-8B`                |       8.2B | Yes                       | Yes                             | Yes                                | Yes                                | Good entry-level choice                                                                 |
| `Llama-3.1-8B-Instruct`   |         8B | Yes                       | Yes                             | Yes                                | Yes                                | Safe and practical                                                                      |
| `Granite-3.1-8B-Instruct` |         8B | Yes                       | Yes                             | Yes                                | Yes                                | Good compact alternative                                                                |
| `Gemma-4-12B-QAT`         |        12B | Maybe                     | Yes                             | Yes                                | Yes                                | Strong option, but `12 GB` VRAM is tight                                                |
| `Gemma-4-26B-A4B`         |    26B A4B | No                        | Maybe                           | Maybe                              | Yes                                | Very strong option on higher-memory Apple Silicon, but too heavy for smaller GPU setups |

Interpretation:
- `Yes` means the setup is generally workable for this app at the recommended context range for that machine.
- `Maybe` means it can work, but it is more sensitive to quantization, runtime overhead, and context length.
- `No` means it is usually not a practical match for this storyteller use case.

Note:
On an Apple Silicon machine with enough unified memory, especially `64 GB`, `Gemma-4-26B-A4B` will often not only produce better results, but will likely also respond noticeably faster than dense `8B` or `12B` models.

Practical guidance:
- `RTX 3080 Ti 12 GB`: mainly suitable for `8B` models, and borderline for some `12B` setups.
- `RTX 4090 24 GB`: a strong fit for `8B` and `12B` models at moderate context sizes.
- `Mac 32 GB unified`: a good fit for `8B` and `12B` models.
- `Mac 64 GB unified`: the most comfortable option here, with much more room for larger models and longer context windows.

### Note

On the reference machine above, a larger local model such as Gemma 4 26B 6-bit typically uses about `32 to 36 GB` of shared memory in practice:

- about `22 GB` for the model itself
- about `8 to 12 GB` for a `32K to 48K` context window
- about `2 GB` for runtime overhead

These numbers are practical estimates, not hard guarantees. Actual memory use depends on the exact quantization, runtime, backend, and context length.

## Performance & Memory Architecture (Apple Silicon / LM Studio)

Long-context local models, especially larger Mixture-of-Experts models such as Gemma 4 26B-A4B or Qwen 3 Coder 30B, trade context depth against memory pressure and desktop responsiveness. A 64 GB Apple Silicon machine can be a strong long-context setup, but there is no universal safe `65K` configuration: model format, quantization, runtime, vision support, and the loaded context length all materially affect memory use.

### Sequential derived-memory queue

A completed story turn can schedule three derived-memory refreshes: long-term summary, recent summary, and canonical state. These jobs are deliberately submitted to one daemon-backed `DerivedMemoryTaskQueue` and run sequentially.

- This keeps derived-memory requests off the foreground story path.
- It avoids the application itself starting three simultaneous background inference calls.
- It reduces peak pressure on the backend, but does not override the backend's own parallelism or resource settings.

### Periodic cache busting and drift

Long prompts can make a model less attentive to older instructions or world constraints. The app offers a portable best-effort mitigation: every `cacheBuster.interval` persisted story turns (default `5`), it sends an internal reset request with a unique token prepended to the system prompt. The response is discarded; no extra message is shown to the user. `Ctrl-U` undo always sends the same reset-with-cache-buster request after removing the last turn.

This is not a documented KV-cache flush and does not guarantee that a backend discards a cache. It deliberately changes the prompt prefix so cache-sensitive OpenAI-compatible backends are less likely to reuse an exact stale prefix. Set `cacheBuster.interval=0` to disable periodic requests.

### LM Studio starting points for a 64 GB Mac

Use LM Studio's memory estimator before loading a model and verify the actual configuration after loading. The values below are starting points, not guarantees or application requirements. LM Studio supports configuring context length, evaluation batch size, Flash Attention, GPU offload, and model parallelism at load time; support varies by engine and model format.

| Setting               | Suggested starting point                              | Why                                                                                                       |
|-----------------------|-------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| Context length        | Start at `32K`; raise incrementally after testing     | Context length is a primary driver of KV-cache memory use.                                                |
| GPU offload           | `max` when the estimator shows sufficient headroom    | Maximizes accelerator use; leave room for macOS and other applications.                                   |
| Flash Attention       | Enabled when supported                                | Can reduce attention memory use and improve speed on llama.cpp-based models.                              |
| Evaluation batch size | `512`, then tune                                      | Smaller values reduce prompt-ingestion peaks at the cost of throughput.                                   |
| Parallel predictions  | `1` or `2` for tight memory budgets                   | The Java queue serializes derived-memory work, but backend parallelism can still increase resource use.   |
| KV-cache quantization | Test the available option for the loaded engine/model | It can substantially change memory use and quality; do not assume one quantization is optimal everywhere. |

CPU-thread, physical-batch, and KV-cache settings differ across LM Studio engines and model formats. Prefer LM Studio's current model-specific controls and estimator over fixed thread counts or OS-level memory-limit overrides. See the [LM Studio model-loading documentation](https://lmstudio.ai/docs/developer/rest/load) and [CLI resource estimator](https://lmstudio.ai/docs/cli/local-models/load).

### KV-cache quantization

For long contexts, KV-cache quantization is often one of the most effective memory-saving controls because KV-cache use grows with context length. It is distinct from the quantization of the model weights themselves.

- Start with `q8_0` for the K and V caches when it is available: it is usually the more conservative memory-versus-quality trade-off.
- Try `q4_0` for both caches when memory pressure or swapping remains a problem. It can reduce KV-cache memory substantially, but may affect output quality, especially for a particular model or very long context.
- Keep Flash Attention enabled when the selected engine requires it for quantized K/V caches, and test a representative long story prompt after changing the setting.

These are backend load settings, not OpenAI-compatible request parameters, so Storyteller does not set them automatically. Jan's llama.cpp engine exposes `q8_0` and `q4_0` K/V-cache options for memory-constrained setups; LM Studio availability depends on the active engine and model format. See the [Jan llama.cpp engine guide](https://jan.ai/docs/llama-cpp) and [LM Studio load configuration reference](https://beta.lmstudio.ai/docs/typescript/api-reference/llm-load-model-config).

### Thread allocation detail (Apple Silicon)

On an M1 Max, the CPU has eight performance cores and two efficiency cores. If LM Studio exposes a CPU-thread control for the active runtime, `8` is a sensible throughput-oriented starting point because it can keep the performance cores busy. `6` is a sensible responsiveness-oriented starting point when IntelliJ, a browser, or compilation should retain more CPU headroom.

Do not assume that `7` is universally the default or that powers of two are inherently optimal: the best value depends on the LM Studio engine, model format, GPU offload level, and what else the machine is doing. Measure generation speed and UI responsiveness with the target model, then keep the lowest value that gives the desired experience.

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

### Optional local overrides

If you create a local `systemprompts/` folder in the working directory with files of the same names, those files override the bundled defaults.

An example override folder is included at [`systemprompts.example/`](systemprompts.example), including mode-specific examples such as [`cowriter_story`](systemprompts.example/cowriter_story) and [`dungeons_dragons`](systemprompts.example/dungeons_dragons).
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

These files and their parent `memory/` directory may start out missing. The app creates and updates them as needed.

### Knowledge graph MVP

Version 1.2.0 introduces a small knowledge graph for mitigation of entity contagion and feature bleeding.
Storyteller loads and validates `memory/knowledge-graph.json` automatically when it exists (configurable through `file.knowledgeGraph`). Changes made while Storyteller is running are picked up automatically before graph facts are used; an invalid intermediate edit never replaces the last valid in-memory snapshot.
When the current user input or candidate response mentions an entity name or alias, its active hard facts are injected into both the story and validation prompts.
Missing files and turns without matching entities preserve the existing behavior.

The initial closed ontology supports:

- entity types `CHARACTER`, `ITEM`, and `SKILL`;
- predicates `POSSESSES` (`CHARACTER` to `ITEM`), `CAN_PERFORM` (`CHARACTER` to `SKILL`), and directional `LOVES` (`CHARACTER` to `CHARACTER`);
- positive and negative active facts, with absent facts resolving to `UNKNOWN`;
- fixed fact sources, statuses, aliases, revision and schema metadata;
- strict entity-reference, predicate-type, duplicate, and contradiction validation;
- immutable in-memory indexes for entity, alias, subject, object, and truth lookup;
- validated, atomically replaced JSON persistence through `KnowledgeGraphStore`;
- reflection-free graph JSON encoding and decoding for GraalVM native images;
- bounded name/alias retrieval and prompt grounding for active hard facts.

The CLI provides three graph management commands. They are local control commands and are never recorded as story turns:

- `/graph` displays the current graph and configured JSON path without calling the model;
- `/graph -generate` creates and immediately publishes a minimal empty graph document without calling the model;
- `/graph -fill` sends only the complete configured `fixed_protagonists.yml` content to the model, validates and normalizes the returned closed-ontology graph in Java, atomically replaces the graph file, and immediately publishes the new snapshot.

`/graph -generate` and a successful `/graph -fill` replace the existing graph. `/graph -fill` forces extracted facts to `ACTIVE`, `FIXED_PROTAGONIST`, and `hard=true`; Java controls schema version and revision. Invalid model JSON or a graph validation failure leaves the existing persisted graph and active snapshot unchanged. Normal story turns remain read-only with respect to graph persistence; automatic per-turn mutation remains a later phase documented in [`graph_feature_bleeding_mitigation.md`](docs/architecture/graph_feature_bleeding_mitigation.md).

## Configuration

All default configuration values now come from bundled `application.config`, not hard-coded Java defaults.

By default, `model.chat` and `model.validator` are left blank. For an OpenAI-compatible backend the app then omits the `model` field, so LM Studio, Jan.ai, or another compatible backend can use its currently loaded, selected, or default model automatically. A managed MLX server instead uses `backend.mlx.modelPath` as the request model automatically.

`backend.type=openai-compatible` is the default and sends requests to `backend.http.url`; it works with LM Studio, Jan, Ollama, OpenAI-compatible hosted APIs, and externally started model servers. Set `backend.type=managed-llama-server` to start a local `llama-server` process for GGUF, or `backend.type=managed-mlx-server` to start `mlx_vlm.server` for a local MLX model. Both managed servers bind only to `127.0.0.1` and stop when the application closes.

For example:

```properties
backend.type=managed-llama-server
backend.llama.command=llama-server
backend.llama.modelPath=./models/Gemma4-26B-A4B-QAT.gguf
backend.llama.port=0
backend.llama.startupTimeoutSeconds=120
backend.llama.arguments=--ctx-size 32768 --ctx-checkpoints 32 --parallel 3 --threads 6 --threads-batch 6 --batch-size 1024 --ubatch-size 512 --kv-offload --cache-type-k q8_0 --cache-type-v q8_0 --flash-attn on --reasoning off
```

For a local MLX model on Apple Silicon:

```properties
backend.type=managed-mlx-server
backend.mlx.command=python3
backend.mlx.modelPath=./model/gemma-4-26b-a4b-it-4bit
backend.mlx.port=0
backend.mlx.startupTimeoutSeconds=180
backend.mlx.arguments=-m mlx_vlm.server --max-kv-size 32768 --max-tokens 32768
```

Install `mlx-vlm` for the configured Python interpreter and ensure `python3` is available on `PATH`. The managed MLX server is intended for local use and uses its OpenAI-compatible `/v1/chat/completions` endpoint.

The bundled defaults do not select a managed-server command, model, or model-specific arguments. Configure those values in a local `application.config` or start from the examples in `systemprompts.example/llama_server` and `systemprompts.example/mlx_server`. An MoE model's active expert count is encoded by the model itself; `--n-cpu-moe` means something different and would keep MoE layers on the CPU.

Legacy local overrides using `lmstudio.url` remain accepted and map to `backend.http.url`.

Configuration is now split into:
- [`AppConfigLoader.java`](src/main/java/nl/llm/storyteller/core/AppConfigLoader.java): loads bundled defaults, local overrides, and native-runtime overrides
- [`AppConfigSource.java`](src/main/java/nl/llm/storyteller/core/AppConfigSource.java): typed access to merged raw properties
- [`AppConfig.java`](src/main/java/nl/llm/storyteller/core/AppConfig.java): validated runtime view used by the app

Important settings:
- `chat.maxRecentTurns=2`
- `recentSummary.maxRecentTurns=12`
- `recentSummary.batchMessages=6`
- `summary.batchMessages=10`
- `canonicalState.batchMessages=2`
- `cacheBuster.interval=5` (`0` disables periodic cache busters)
- `validation.enabled=true`
- `validation.outputMode=auto` requests JSON Schema constrained validation when supported and retries as text when the endpoint rejects it; the rejection is remembered for the active session
- `resilience.chat.failureThreshold=3`
- `resilience.chat.cooldownSeconds=20`
- `resilience.validation.failureThreshold=2`
- `resilience.validation.cooldownSeconds=15`
- `resilience.background.failureThreshold=2`
- `resilience.background.cooldownSeconds=60`

If a model behaves badly with the rules engine, disable it with:

```properties
validation.enabled=false
```

When validation is disabled, `rules.md` is skipped and the raw model answer is returned directly.
When validation is enabled, the validator returns an `ALLOW` or `REPLACE` decision. By default its request includes an OpenAI-compatible JSON Schema response format so supporting backends constrain that decision during generation. The schema is deliberately used only for validation; story responses remain free text. Older compatible endpoints automatically use the existing tolerant text path when they reject structured output. Set `validation.outputMode=text` for a legacy endpoint, or `json-schema` to require constrained validator output.

The app also uses a small built-in resilience layer around LLM calls:
- foreground chat calls use a short cooldown-based fail-fast guard
- validation calls use their own guard and remain independent from the foreground response policy
- background memory refresh calls use a more aggressive cooldown so repeated summary/state updates stop hammering an unavailable backend

## Prompt Assembly

The app does not send the full history back to the model. It sends:
- one combined first `system` message containing:
  the main system prompt
  fixed protagonists
  canonical state
  long-term summary
  recent summary
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
- [`AppConfigLoader.java`](src/main/java/nl/llm/storyteller/core/AppConfigLoader.java) and [`AppConfigSource.java`](src/main/java/nl/llm/storyteller/core/AppConfigSource.java): loading, merging, and path resolution
- [`AppConfig.java`](src/main/java/nl/llm/storyteller/core/AppConfig.java): validated runtime settings only

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
- Added the bounded `nl.llm.storyteller.core.graph` capability for graph-based mitigation of entity contagion and feature bleeding.
- Added a deliberately closed first ontology containing `CHARACTER`, `ITEM`, `SKILL`, `POSSESSES`, and `CAN_PERFORM`.
- Added directional positive and negative `LOVES` facts to prevent romantic relationships from bleeding between characters.
- Added strict graph document validation, immutable lookup snapshots, explicit `TRUE`/`FALSE`/`UNKNOWN` truth semantics, and atomically replaced JSON persistence.
- Added automatic graph loading at startup and before runtime fact retrieval, while retaining the last valid snapshot during incomplete or invalid file edits.
- Added bounded active-hard-fact injection into both story generation and validation prompts.
- Added local `/graph` inspection and `/graph -generate` empty-document creation without model calls.
- Added `/graph -fill` for model-assisted extraction from only the configured fixed-protagonist document, followed by deterministic Java normalization, validation, atomic persistence, and snapshot publication.
- Added reflection-free graph JSON encoding and decoding so graph loading, generation, filling, and persistence work in GraalVM native executables.
- Added focused tests for graph loading, round-trip persistence, entity references, predicate constraints, aliases, contradictions, immutability, unknown facts, automatic runtime loading, empty initialization, fixed-protagonist-only fill input, model-result normalization, and failure preservation.
- Added the mitigation design and updated the README, component design, and flow design for the active graph runtime slice and remaining future phases.

### 1.1.5
- Fixed managed MLX requests so `model.chat` and `model.validator` no longer need to duplicate `backend.mlx.modelPath`; when left blank, the managed model path is now sent automatically.

### 1.1.4
- Added `/image <instruction>` for sending an image copied to the macOS or Windows desktop clipboard as a transient base64 vision input.
- Kept clipboard image data out of history, validation, derived-memory updates, and story exports; only instruction and response text are persisted.

### 1.1.3
- Added an optional managed `mlx_vlm.server` backend for local MLX models, including process startup, dynamic loopback ports, readiness checks, and lifecycle shutdown.
- Added a mlx-vlm example configuration and documented the managed MLX backend in the README and PlantUML architecture diagrams.

### 1.1.2
- Added OpenAI-compatible JSON Schema structured output for validator decisions, with `auto`, `text`, and required `json-schema` output modes.
- In automatic mode, validation falls back once to the existing tolerant plain-text parser when a backend rejects `response_format`; that capability result is cached for the remaining application session.
- Added llama.cpp managed-server examples for a 32K context, 32 context checkpoints, one server slot, CPU/batch sizing, GPU KV-cache offload, Q8_0 K/V cache, Flash Attention, and disabled reasoning.

### 1.1.1
- Enabled GraalVM shared-arena support for the JLine 4 terminal provider in native builds.

### 1.1.0
- Reorganized the single deployable application into explicit `nl.llm.storyteller.core` and `nl.llm.storyteller.cli` packages, retaining one Maven project, one runnable shaded jar, and the existing CLI behavior.
- Documented the local browser API boundary and session lifecycle design, including the accompanying API architecture diagram, while keeping HTTP implementation outside the core for now.
- Refactored validator decision parsing into focused JSON, structured-node, and tolerant-text helpers; explicit structured decisions now take precedence over incidental decision words in response text.
- Removed Maven Shade warnings by retaining one Jackson license/notice pair and filtering duplicate manifests, module descriptors, and identical license resources from the shaded jar.

### 1.0.11
- Split the terminal application into dedicated composition, terminal-controller, and renderer classes so display formatting can be unit-tested without JLine.
- Added renderer tests for word wrapping and fenced code blocks, and aligned the derived-memory queue and cache-buster tests with the Given/When/Then display-name convention.

### 1.0.10
- Replaced the three independent derived-memory executors with one shared sequential task queue so background refreshes cannot call the LLM backend concurrently.
- Added configurable periodic cache-buster requests after persisted story turns (`cacheBuster.interval=5`, `0` to disable), while undo always retains its cache-buster reset.
- Added Apple Silicon / LM Studio performance guidance with conservative long-context and memory-tuning starting points.
- Added configuration and queue-ordering test coverage and updated the architecture documentation.

### 1.0.9
- Added `Ctrl-U` / `Cmd-U` as an undo-and-retry control action that removes the last persisted turn, sends a transient reset request, and restores the previous user prompt into the input buffer for editing.
- Added `Ctrl-L` / `Cmd-L` as a local read-only shortcut that shows the last persisted user prompt and assistant reply without sending anything to the model.
- Fixed reset-only `Ctrl-W` turns so they are treated as transient control requests instead of normal story turns.
- Fixed reset-only turns to stay out of `history.json` and to skip long-term summary, recent summary, and canonical state refresh triggers.
- Added a transient request-local cache-buster token to reset-only turns as a presumed best-effort portable cache-break for LM Studio and other OpenAI-compatible backends that do not expose a standard per-request KV-cache flush or slot-selection API.
- Fixed history rollback so undoing the last turn safely clamps the summary, recent-summary, and canonical-state cursors to the shortened `history.json`.
- Added a history helper for retrieving the latest persisted turn as a user+assistant pair.
- Documented the new last-turn inspection shortcut and the transient undo/reset control flow in the README.
- Documented the transient undo/reset control flow and its non-persisted behavior in the README.

### 1.0.8
- Fixed prompt assembly for stricter LM Studio and OpenAI-compatible chat templates by sending story chat as one combined first `system` message instead of multiple separate `system` messages.
- Fixed long-term summary, recent summary, and canonical state background updates to use the same single-system-message layout, preventing LM Studio template failures on derived-memory refresh calls.

### 1.0.7
- Updated the release packaging flow to publish the runnable shaded jar as `storyteller-<version>-all.jar`, avoiding self-overlap warnings on repeated Maven package or verify runs.

### 1.0.6
- Added an optional engine-level turn-based game mode that tracks round participation outside the LLM and injects prompt penalties for illegal extra moves.
- Added persistent turn-state storage in `memory/turn-state.json` and integrated turn-rule evaluation into prompt assembly before each story turn.
- Updated the Dungeons & Dragons example configuration to demonstrate turn-based mode defaults and revised the example READMEs to use a more consistent structure.
- Updated the README and PlantUML diagrams to document the new turn-based game flow and example-mode positioning.

### 1.0.5
- Added the first `systemprompts.example/` mode examples as reusable reference configurations for alternative storyteller setups.

### 1.0.4
- Fixed validator rewrite handling so `REPLACE` responses now use the corrected text instead of falling back to the fail-closed warning message.
- Made validator parsing more tolerant for smaller or less strict models by supporting wrapped rewrite payloads in `content`, `message.content`, and full chat-completion `choices[0].message.content` envelopes.
- Added test coverage for plain-text validator rewrite payloads such as `REPLACE: ...` and `REPLACE` followed by corrected text on the next line.

### 1.0.3
- Switched terminal control commands from plain `exit` and `quit` to `/exit` and `/quit`
- Added Markdown story export commands: `/export`, `/export -intro`, `/export -clean`, and `/export -all`
- Added `StoryExportService` to export story history into Markdown files in the application working directory
- Updated the README and PlantUML diagrams to document the new command and export flow

### 1.0.2
- Added more test coverage
- Added a lightweight resilience layer for repeated LLM backend failures, with separate cooldown policies for chat, validation, and background memory refreshes

### 1.0.1
- Extracted prompt construction into dedicated builder services for chat, validation, summary, recent summary, and canonical state updates.
- Added small prompt-input records in `model` so prompt builders no longer depend on long ordered `String` parameter lists.
- Moved `ValidationOutcome` into the `model` package as a pure decision/result type.
- Simplified `DerivedMemoryManager` by removing unused prompt helper code and renaming the enablement hook to `isDisabled()` for clearer control flow.
- Tightened small parser and config cleanups, including the redundant null check in `ValidationDecisionParser` and a smaller top-level `AppConfig` constructor shape.
- Updated the README and PlantUML diagrams to match the current storyteller prompt and validation architecture.
