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

### Note

On the reference machine above, a larger local model such as Gemma 4 26B 6-bit typically uses about `32 to 36 GB` of shared memory in practice:

- about `22 GB` for the model itself
- about `8 to 12 GB` for a `32K to 48K` context window
- about `2 GB` for runtime overhead

These numbers are practical estimates, not hard guarantees. Actual memory use depends on the exact quantization, runtime, backend, and context length.

## Used Tools / Hardware

- [Aider](https://github.com/Aider-AI/aider) for code generation and refactoring using a local llm-server
- [LM Studio](https://lmstudio.ai/docs/developer) for model management, local serving, and OpenAI-compatible API access
- [Jan](https://www.jan.ai/docs) for compatibility testing
- [ChatGPT](https://chatgpt.com/) for discussing design decisions and improving system prompts
- [mlx-community/gemma-4-26B-A4B-it-qat-6bit](https://huggingface.co/mlx-community/gemma-4-26B-A4B-it-qat-6bit) for local story-model testing
- [Qwen/Qwen3-Coder-30B-A3B-Instruct](https://huggingface.co/Qwen/Qwen3-Coder-30B-A3B-Instruct) as a local coding model during development
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
java -jar target/storyteller-1.0.0.jar
```

The local default build version is `1.0.0`.
GitHub releases use automatic patch versioning on every push to `main`, producing tags and release jars such as `v1.0.1`, `v1.0.2`, `v1.0.3`, and so on.
Each push to `main`, including merges from pull requests, automatically builds a release jar and publishes it to GitHub Releases.

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

An example override folder is included at [`systemprompts.example/`](systemprompts.example), including an example [`systemprompt.md`](systemprompts.example/systemprompt.md) and a more extensive [`fixed_protagonists.yml`](systemprompts.example/fixed_protagonists.yml) that shows multiple characters plus optional sections such as `living_environment`, `world_view`, `world_physics`, and character-specific `hard_constraints`.

### Runtime memory

The app reads and writes story memory in `memory/`:
- `history.json`
- `history.md`
- `summary.md`
- `recent-summary.md`
- `canonical-state.yaml`

These files and their parent `memory/` directory may start out missing. The app creates and updates them as needed.

## Configuration

All default configuration values now come from bundled `application.config`, not hard-coded Java defaults.

By default, `model.chat` and `model.validator` are left blank. In that case the app does not send a `model` field, so LM Studio, Jan.ai, or another compatible backend can use its currently loaded, selected, or default model automatically.

Configuration is now split into:
- [`AppConfigLoader.java`](src/main/java/nl/llm/storyteller/AppConfigLoader.java): loads bundled defaults, local overrides, and native-runtime overrides
- [`AppConfigSource.java`](src/main/java/nl/llm/storyteller/AppConfigSource.java): typed access to merged raw properties
- [`AppConfig.java`](src/main/java/nl/llm/storyteller/AppConfig.java): validated runtime view used by the app

Important settings:
- `chat.maxRecentTurns=2`
- `recentSummary.maxRecentTurns=12`
- `recentSummary.batchMessages=6`
- `summary.batchMessages=10`
- `canonicalState.batchMessages=2`
- `validation.enabled=true`
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
When validation is enabled, the validator now either returns `ALLOW` or returns corrected replacement text that becomes the final response.

The app also uses a small built-in resilience layer around LLM calls:
- foreground chat calls use a short cooldown-based fail-fast guard
- validation calls use their own guard and remain independent from the foreground response policy
- background memory refresh calls use a more aggressive cooldown so repeated summary/state updates stop hammering an unavailable backend

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

Important runtime detail:
- the foreground story turn stays synchronous for prompt assembly, model response, validation, and history append
- the derived-memory refreshes for `summary.md`, `recent-summary.md`, and `canonical-state.yaml` are triggered asynchronously afterward
- each derived-memory manager runs its own single-threaded background worker, so those LLM calls do not block the user from getting the current story response

## Runtime Structure

The runtime responsibilities are now split more explicitly:
- [`AssistantApp.java`](src/main/java/nl/llm/storyteller/AssistantApp.java): terminal bootstrap, shortcut registration, input loop, and formatted output
- [`StorySessionService.java`](src/main/java/nl/llm/storyteller/service/StorySessionService.java): prompt assembly, model call, validation, history append, and derived-memory refresh triggering
- [`PromptAssemblyService.java`](src/main/java/nl/llm/storyteller/service/PromptAssemblyService.java): coordinates prompt building from prompts, memory, and recent turns

Prompt responsibilities are now split more explicitly:
- [`PromptResourceLoader.java`](src/main/java/nl/llm/storyteller/service/PromptResourceLoader.java): loads raw prompt resources
- [`PromptTemplateService.java`](src/main/java/nl/llm/storyteller/service/PromptTemplateService.java): formats reusable prompt fragments
- [`StoryChatPromptBuilder.java`](src/main/java/nl/llm/storyteller/service/StoryChatPromptBuilder.java): builds the main storyteller chat stack
- [`ValidationPromptBuilder.java`](src/main/java/nl/llm/storyteller/service/ValidationPromptBuilder.java): builds validator system and user payloads
- [`SummaryPromptBuilder.java`](src/main/java/nl/llm/storyteller/service/SummaryPromptBuilder.java), [`RecentSummaryPromptBuilder.java`](src/main/java/nl/llm/storyteller/service/RecentSummaryPromptBuilder.java), and [`CanonicalStatePromptBuilder.java`](src/main/java/nl/llm/storyteller/service/CanonicalStatePromptBuilder.java): build the three derived-memory update prompts

Those builders now take small prompt-input records from [`src/main/java/nl/llm/storyteller/model`](src/main/java/nl/llm/storyteller/model), so prompt inputs are explicit instead of being passed around as long ordered `String` argument lists.

Configuration follows the same separation:
- [`AppConfigLoader.java`](src/main/java/nl/llm/storyteller/AppConfigLoader.java) and [`AppConfigSource.java`](src/main/java/nl/llm/storyteller/AppConfigSource.java): loading, merging, and path resolution
- [`AppConfig.java`](src/main/java/nl/llm/storyteller/AppConfig.java): validated runtime settings only

Validation is also split into focused parts:
- [`ValidationClient.java`](src/main/java/nl/llm/storyteller/service/ValidationClient.java): sends the validator prompt to the configured model
- [`ValidationDecisionParser.java`](src/main/java/nl/llm/storyteller/service/ValidationDecisionParser.java): extracts `ALLOW` or `REPLACE` from structured or plain-text validator output
- [`ValidationOutcome.java`](src/main/java/nl/llm/storyteller/model/ValidationOutcome.java): compact validation decision model with small decision helpers
- [`ResponseSanitizer.java`](src/main/java/nl/llm/storyteller/service/ResponseSanitizer.java): cleans visible JSON-style escapes before terminal output
- [`ResponseGuard.java`](src/main/java/nl/llm/storyteller/service/ResponseGuard.java): coordinates those parts, including validator-provided replacement text when a rewrite is needed

LLM backend resilience is handled separately:
- [`ResilientChatClient.java`](src/main/java/nl/llm/storyteller/service/ResilientChatClient.java): wraps a `ChatClient` with fail-fast cooldown behavior
- [`LlmBackendGuard.java`](src/main/java/nl/llm/storyteller/service/LlmBackendGuard.java): tracks repeated failures and temporarily opens a cooldown window after the configured threshold

The three derived-memory updaters now share one common infrastructure layer:
- [`DerivedMemoryManager.java`](src/main/java/nl/llm/storyteller/service/DerivedMemoryManager.java): worker lifecycle, concurrency guard, model-call flow, and safe write-back coordination
- [`SummaryManager.java`](src/main/java/nl/llm/storyteller/service/SummaryManager.java), [`RecentSummaryManager.java`](src/main/java/nl/llm/storyteller/service/RecentSummaryManager.java), and [`CanonicalStateManager.java`](src/main/java/nl/llm/storyteller/service/CanonicalStateManager.java): their own cutoff rules and prompt contents

Those background memory refreshes are asynchronous by design:
- `StorySessionService` triggers them after the current turn has already been appended to history
- each manager uses its own daemon-backed single-thread executor
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

### 1.0.2
- Added more test coverage
- Added a lightweight resilience layer for repeated LLM backend failures, with separate cooldown policies for chat, validation, and background memory refreshes

### 1.0.1
- Extracted prompt construction into dedicated builder services for chat, validation, summary, recent summary, and canonical state updates.
- Added small prompt-input records in `model` so prompt builders no longer depend on long ordered `String` parameter lists.
- Moved `ValidationOutcome` into the `model` package as a pure decision/result type.
- Simplified `DerivedMemoryManager` by removing unused prompt helper code and renaming the enablement hook to `isDisabled()` for clearer control flow.
- Tightened small parser and config cleanups, including the redundant null check in `ValidationDecisionParser` and a smaller top-level `AppConfig` constructor shape.
- Added a `systemprompts.example/` folder with English example prompt files, including a narrative-engine `systemprompt.md` and a `fixed_protagonists.yml` example with top-level `living_environment`, `world_view`, and `world_physics`.
- Updated the README and PlantUML diagrams to match the current storyteller prompt and validation architecture.
