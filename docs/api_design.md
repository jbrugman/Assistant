# Storyteller API Design

## Purpose

The Storyteller API provides an HTTP interface and a default server-rendered web interface for Storyteller. It keeps session state on the server so clients send commands and content instead of resending the complete story state.

The API is one interface to the application. The CLI remains supported, and other clients can be built against the JSON API.

Related diagrams:

- [API component design](architecture/api/component-design.puml)
- [API flow design](architecture/api/flow-design.puml)
- [Database component design](architecture/db/component-design.puml)
- [Database schema design](architecture/db/schema-design.puml)
- [Web interface design](webpage-design.md)

## Module Boundaries

The Maven modules have separate responsibilities:

| Module | Responsibility |
|---|---|
| `storyteller-core` | Story generation, prompt assembly, validation, derived memory, knowledge-graph behavior, and domain models. |
| `storyteller-db` | JDBC repositories, H2 schema, and database-backed session persistence shared by the CLI and API. |
| `storyteller-cli` | Terminal interface and JLine integration. Depends on Core and DB. |
| `storyteller-api` | Javalin controllers, JTE pages, API configuration, HTTP/TLS lifecycle, and web assets. Depends on Core and DB. |

Core must not depend on CLI, Javalin, JTE, JDBC, or H2. Controllers remain thin and delegate application behavior to services. Database code remains outside both interface modules.

The API uses Core's prompt construction, model client, backend guard, validation, and memory components. It does not route requests through the terminal-oriented Core `StorySessionService`; the API has its own database-backed session orchestration.

## Runtime Design

### Backend

The API talks to an already running OpenAI-compatible backend, such as LM Studio. It does not start or stop `llama-server`, MLX, or another inference process. Backend selection and lifecycle are external to the API process.

### HTTP and server-side rendering

Javalin owns HTTP routing and server lifecycle. JTE renders the bundled default web interface on the server. This keeps the browser client small and avoids requiring a separate JavaScript framework or frontend deployment.

The default web interface is optional from an architectural perspective: another client can use the JSON API. The rationale for SSR and JTE is documented in the [web interface design](webpage-design.md).

### Configuration

The API owns its configuration and bundled defaults in:

```text
storyteller-api/src/main/resources/application.config
```

A runtime `application.config` overrides those defaults. API configuration is not stored in the Core configuration file.

Current settings include:

- `api.host`
- `api.port`
- `api.database.path`
- `api.database.username`
- `api.database.password`
- `api.sessionTimeoutMinutes`
- `api.tls.enabled`
- `api.tls.port`
- `api.tls.directory`
- `api.tls.subjectAlternativeNames`

The default HTTP listener uses port `7070`. When TLS is enabled, HTTPS uses port `7443`; both connectors belong to the same API application. The generated local certificate authority is available at `GET /storyteller-ca.crt`. Session cookies are marked `Secure` when TLS is enabled.

### Packaging

The API is packaged separately from the CLI so the CLI distribution does not include Javalin, Jetty, JTE, H2 web dependencies, or API resources. Both remain modules of the same Maven project.

## Sessions

The server owns session history, prompts, canonical state, summaries, knowledge-graph data, images, activity timestamps, and lifetime policy.

For a new session, `systemprompt.md`, `fixed_protagonists.yml`, and `rules.md` are resolved from local overrides or
bundled resources and copied into H2 as initial defaults. The stored session values are authoritative afterward;
restarting the application or changing the source files does not overwrite an existing session. Internal validation,
summary, canonical-state, and graph templates remain application resources and are not editable session data.

The browser stores only the active session identifier in a cookie. An infinite session can be reopened in another browser when its session ID is known. Non-infinite sessions expire after the configured inactivity period.

The CLI uses a fixed database session ID:

```text
00000000-0000-0000-0000-000000000001
```

This permits the same persisted story to be used through CLI and API interfaces. Embedded H2 file access still permits only one owning process at a time; the CLI and API must not open the same embedded database concurrently.

Stopping a session permanently removes the session and its dependent records. Undo removes the most recent persisted exchange and its derived data through the repository transaction designed for that operation.

## Persistence

H2 is the current embedded database, accessed through portable JDBC and standard SQL where practical. There is no ORM and no Flyway dependency.

The canonical schema lives in [schema.sql](../storyteller-db/src/main/resources/db/schema.sql). The [database schema diagram](architecture/db/schema-design.puml) provides a readable overview. This document deliberately does not duplicate column definitions because the executable schema is authoritative.

Repository implementations own transaction boundaries for multi-table operations such as import, deletion, and undo. A
normal story turn is persisted before long-term history, recent history, canonical state, and the turn-based knowledge
graph are updated asynchronously; those operations are not falsely presented as one database transaction.

Database-backed state includes:

- sessions and activity/lifetime settings
- ordered story messages
- optional image payloads and media metadata
- system prompt, fixed protagonist, and rules
- canonical state
- medium- and long-term history
- knowledge-graph documents
- selected older exchanges used as additional context

The settings page can edit the system prompt, fixed protagonist, rules, and validated knowledge graph. Medium- and
long-term history and canonical state are read-only in the web interface.

### Knowledge graph

Knowledge-graph data is persisted and included in session import/export. The API story-turn path injects relevant
session graph facts into model context and schedules turn-based extraction after a persisted turn. Extraction runs when
the configured `graph.turnBased.batchTurns` threshold is reached and stores the resulting revision in the same session.
The graph update uses the same Core extraction and validation behavior as the CLI.

### Images

Prompt images are stored as BLOB data with their message record, not as ordinary files beside the database. The web interface accepts pasted images, shows a clickable thumbnail beside the associated prompt, and presents the full image in a dismissible overlay.

Exports write images to `memory/images/NNN.ext`, where the number corresponds to the message index. Imports restore supported images and their association with the prompt.

## Story Context

The regular context window contains the configured number of recent exchanges. The web interface can additionally select a small number of older exchanges that fall outside this window. These are explicitly labelled as relevant past events when sent to the model.

Selection is limited so this feature cannot silently replace normal context management or inflate requests without bound.

## Session Bundles

ZIP import/export is the portable backup and migration format. A bundle can contain:

- `history.json`
- prompt override files under `systemprompts/`
- canonical and derived memory
- knowledge-graph data
- prompt images under `memory/images/`

Newer bundle entries are optional during import so older CLI exports remain usable. Import validates entry names, ignores platform metadata such as macOS `__MACOSX` entries, limits uncompressed content, validates supported JSON/YAML structures, and persists the imported session in a repository transaction.

## Implemented JSON API

The current public JSON surface is intentionally small:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v1/sessions` | Create a database-backed story session. |
| `GET` | `/v1/session` | Resolve the active browser session. |
| `GET` | `/v1/sessions/{sessionId}` | Read a session by ID. |
| `POST` | `/v1/sessions/{sessionId}/turns` | Submit a story turn and return the generated reply. |

Unknown sessions, invalid input, conflicts, backend failures, and persistence failures are mapped to stable HTTP error responses. SQL is always parameterized; input normalization complements validation but is not treated as a substitute for prepared statements.

## Implemented Web Routes

The bundled web interface currently uses these server-rendered routes:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/` | Start or landing page. |
| `POST` | `/web/sessions` | Create a web session. |
| `POST` | `/web/sessions/resume` | Resume by session ID. |
| `GET` | `/story/resume/{sessionId}` | Open a resumable session. |
| `POST` | `/import` | Import a session ZIP. |
| `GET` | `/export` | Download the current session ZIP. |
| `GET` | `/story` | Render the current story. |
| `GET` | `/story/history` | Load an older page of exchanges. |
| `GET` | `/story/images/{messageIndex}` | Return a stored prompt image. |
| `GET`, `POST` | `/story/settings` | View or update editable session state. |
| `GET` | `/story/memory` | View derived history. |
| `POST` | `/story/turns` | Submit a text/image turn. |
| `POST` | `/story/undo` | Undo the latest exchange. |
| `POST` | `/story/infinite` | Change session lifetime policy. |
| `POST` | `/story/stop` | Permanently delete the session. |

History is paged for the web view rather than loading the complete story on every request. Browser actions that mutate a session are disabled while their request is in progress to prevent duplicate submissions.

## Planned JSON API

The following capabilities may receive public JSON endpoints, but are not part of the implemented contract yet:

- paged history and image retrieval
- undo and reset
- prompt and canonical-state management
- derived-memory inspection
- ZIP import/export
- session lifetime changes and deletion
- health/model inspection
- OpenAPI/Swagger description

Web routes are not automatically a stable public JSON contract. A planned endpoint must receive explicit DTOs, validation, error semantics, tests, and documentation before it is listed as implemented.

## Implementation Conventions

- Use two-space indentation in Java and SQL.
- Prefer records for immutable data carriers.
- Keep SQL in dedicated query classes and use prepared statements.
- Keep Javalin `Context` objects inside the controller layer.
- Keep route handlers small and move orchestration into services.
- Use Given/When/Then structure in tests and parameterized tests where appropriate.
- Preserve Core behavior across CLI and API unless an interface-specific difference is explicitly documented.
