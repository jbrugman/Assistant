# Storyteller

Storyteller is a local-first AI storytelling application, but underneath it is really a long-context world preserver.
It keeps characters, relationships, world state, and earlier events coherent across stories that extend far beyond an
LLM's recent conversation window.

Instead of relying on an ever-growing prompt, Storyteller shapes the past into several practical memory layers: recent
conversation, mid-term and long-term summaries, canonical state, and a validated knowledge graph. This deliberately
simple approach gives the model the context it needs at the moment it matters and makes long-running worlds workable
without requiring an enormous context window.

It is a dedicated storyteller and persistent story-world engine, not a general desktop assistant.

The web interface is now the recommended way to use Storyteller. It provides a considerably smoother and more complete
experience than the CLI, especially for navigating long stories, managing sessions, editing settings, attaching images,
and reviewing memory. The CLI remains available for now because it is still useful for benchmarking, diagnostics, and
lightweight terminal-based workflows.

## What it provides

- A terminal interface for interactive storytelling.
- A responsive web interface for mobile, tablet, and desktop.
- A JSON API that can support alternative clients.
- Recent conversation context plus long-term and recent summaries.
- Canonical state and a validated knowledge graph for persistent facts.
- Optional response validation using the same or a separate model.
- Undo, paged web history, portable session import/export, and vision prompts.
- Manual selection of up to three older exchanges as relevant context for the next web turn.
- Support for local or hosted OpenAI-compatible endpoints, Google Gemini Cloud, managed `llama-server`, and managed
  MLX serving.

OpenAI-compatible requests prefer `/v1/responses`. Storyteller caches backend capability and falls back to
`/v1/chat/completions` only when the Responses endpoint is explicitly unavailable. The Responses endpoint requires an
explicit model selection: set `model.chat` and, when validation uses a different model, `model.validator`. When a
separate memory server is configured, set `memory.http.model` for that server. A client without an explicit model uses
Chat Completions directly so the backend can retain its loaded/default-model behavior.

## History
Storyteller started as a small assistant app and gradually evolved into a dedicated storytelling tool.

A deliberate design choice was to keep the codebase framework-light rather than building it around Spring Boot or Quarkus.
The goal was to learn about LLM application design, prompting, memory shaping, and validation behavior, not to spend most of the project inside framework infrastructure.
That choice also helps keep the runtime and source layout relatively small and easy to inspect.

A LLM handled a meaningful share of the routine implementation work, while I remained responsible for the architecture, direction, constraints, review, and final decisions.

### Important: background processing and reasoning

Long-term history, recent history, canonical-state generation, manual `/graph -fill`, and automatic turn-based graph
updates should not spend tokens on model reasoning. With LM Studio, these memory requests therefore send
`reasoning_effort: "none"` through the normal OpenAI-compatible endpoint. Story generation and validation do not.

## Architecture

The project is a framework-light Java modular monolith:

```text
storyteller-core
storyteller-db  ──> storyteller-core
storyteller-cli ──> storyteller-core + storyteller-db
storyteller-api ──> storyteller-core + storyteller-db
```

The CLI and API are separate applications. They share the story engine without pulling each other's interface
dependencies into their distributions. H2 and all JDBC persistence are isolated in `storyteller-db`, so the database
can also become the CLI's persistent session store without introducing an API dependency.

## Requirements

- Java 25 or newer
- Maven
- An OpenAI-compatible chat endpoint with a loaded model

The bundled default configuration connects to LM Studio or another compatible server at:

```text
http://localhost:1234/v1/chat/completions
```

When `backend.type=openai-compatible`, start the external LLM server before Storyteller and make sure an LLM is loaded
or selectable there. Configure model names explicitly to use `/v1/responses`. Empty model fields use Chat Completions
and leave model selection to the backend. This manual startup requirement does not apply to the managed
`llama-server` and MLX backend types: Storyteller starts those configured services itself.

## Build

Build and test every module from the project root:

```bash
mvn clean verify
```

For a faster local package build without rerunning tests:

```bash
mvn -DskipTests package
```

This produces separate runnable CLI and API jars under `storyteller-cli/target/` and `storyteller-api/target/`.

## Use the CLI

After building:

```bash
java -jar storyteller-cli/target/storyteller-cli-*-all.jar
```

Enter a story instruction and press Enter. Type `/exit` or `/quit` to stop. The CLI prints its available commands and
keyboard shortcuts at startup.

To continue an existing API or web session in the CLI, stop the API first and pass its resume ID:

```bash
java -jar storyteller-cli/target/storyteller-cli-*-all.jar \
  --session 05dbf813-8f2b-45f8-abad-93efa01f1199
```

The native executable accepts the same option: `./valerie --session <session-id>`.

## Use the API and web interface

After building:

```bash
java -jar storyteller-api/target/storyteller-api-*-all.jar
```

Or run the API directly with Maven:

```bash
mvn -pl storyteller-api -am exec:java \
  -Dexec.mainClass=nl.llm.storyteller.api.ApiApplication
```

By default, the server listens on:

- `http://localhost:7070`
- `https://localhost:7443`

Open either address in a browser to use the bundled web interface. HTTPS uses a locally generated certificate authority;
clients must trust that local CA to avoid certificate warnings.

An infinite web session displays its resume ID. Enter that ID on the start page, or open
`/story/resume/<session-id>`, to continue the same session in another browser. Sessions with normal inactivity expiry
cannot be resumed this way.

> **Important:** The CLI and API use the same embedded H2 database by default, but embedded H2 allows only one process
> to own that database at a time. Stop the CLI before starting the API, or stop the API before starting the CLI. The
> persisted CLI session can then be resumed through the web interface, and an API/web session can be opened by the
> CLI with `--session <session-id>`. Running both applications simultaneously
> requires separate database paths and therefore does not provide a shared live session.

## Configuration

Storyteller ships with working defaults. To override them, create:

```text
systemprompts/application.config
```

The most common backend settings are:

```properties
backend.type=openai-compatible
backend.http.url=http://localhost:1234/v1/chat/completions
backend.http.apiKey=
model.chat=
model.validator=
memory.http.model=
memory.http.url=
memory.http.apikey=
```

The memory settings configure the shared client for long-term and short-term summaries, canonical state, and graph
generation. Leave them empty to fall back to `model.chat`, `backend.http.url`, and `backend.http.apiKey` respectively.
Main chat, validation, and memory all use the OpenAI-compatible client configured through `backend.http.*`, with
`model.chat`, `model.validator`, and the optional `memory.http.*` overrides.
Leave the model fields empty to use the model already loaded by the backend. CLI, API, and web sessions store their
story state in H2 through `storyteller-db`.

Without `--session`, the CLI uses the fixed infinite session ID `00000000-0000-0000-0000-000000000001`, so its story
can also be resumed through the web interface.

Existing file-backed CLI state is imported into that session once, when it is first created.
For a newly created session, `systemprompt.md`, `fixed_protagonists.yml`, and `rules.md` provide the initial defaults;
the stored H2 values are the live source from then on.
Changing those files does not overwrite prompts already stored for an existing session.

The ZIP format is used for import,
export, and backup rather than as live session storage.
The web **Settings** page edits session prompts and the validated knowledge graph. The read-only **History** page
shows the generated mid-term and long-term summaries plus canonical state.

## Changelog

- https://github.com/jbrugman/Assistant/wiki/Changelog

## More information

- [Technical information, configuration, commands, benchmarks, architecture, and changelog](docs/technical-info.md)
- [API design](docs/api_design.md)
- [Architecture documentation](docs/architecture/)
- [Web interface design and screenshots](docs/webpages-screenshots/)
- [Configuration and Hardware Guide](https://github.com/jbrugman/Assistant/wiki/Configuration-&-Hardware-Guide)

## License

Storyteller is available under the [MIT License](LICENSE).
