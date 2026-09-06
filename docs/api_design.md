# Storyteller API Design

## Purpose

This document captures the current API design decisions for turning the Storyteller CLI into a local or remote API-backed application.
It is intentionally written before the OpenAPI or Swagger specification so the design decisions stay readable and easy to revise.

The accompanying component view is available in [`docs/architecture/03-storyteller-api-design.puml`](architecture/03-storyteller-api-design.puml).

The main goals are:

- keep the existing CLI viable
- make a frontend possible without duplicating storyteller logic
- avoid sending large prompt, history, and state payloads on every request
- keep the first API version simple while establishing database-backed persistence boundaries

## Current Direction

The API should be built around server-owned sessions.

That means:

- the client sends user input and small override changes
- the server owns and stores the session history
- the server owns and stores summaries and canonical state
- the client does not keep sending the entire session state back to the server

This is the main architectural shift away from the current pure CLI flow.

## Non-Goals For The First API Version

The first API version should not try to solve everything at once.

Out of scope for the initial design:

- multi-user authentication
- shared concurrent editing
- model download or model management
- model hosting or inference runtime implementation
- a full Swagger or OpenAPI contract in this phase

## Backend Assumption

The storyteller backend remains an OpenAI-compatible HTTP client. It can target an externally managed endpoint, an optional local `llama-server` process, or an optional local `mlx_vlm.server` process started by the application; all use the same chat-completions adapter.

The API server is not responsible for running models directly.
It should continue to work with:

- LM Studio
- Jan
- Ollama through a compatible API layer
- hosted OpenAI-compatible APIs
- other local or remote compatible backends

## HTTP Framework

The API uses Javalin as its HTTP framework. This keeps the HTTP adapter explicit and lightweight without introducing a full application framework, dependency-injection container, ORM, or framework-owned persistence model.

Javalin is responsible only for:

- HTTP server lifecycle
- route registration and request handling
- request and response serialization through the existing Jackson model
- cookie and header handling
- transport-level validation and error mapping
- response streaming where required

Javalin route handlers must remain thin. They translate HTTP input into application-service calls and translate results into API DTOs. Storyteller orchestration, transaction boundaries, repositories, and domain behavior remain independent of Javalin so the CLI continues to use the same core without an HTTP runtime.

The API module owns its configuration. Its bundled defaults live in `storyteller-api/src/main/resources/application.config`, independently of the core configuration in `storyteller-core/src/main/resources/systemprompts/application.config`. A runtime `application.config` beside the API executable, or in the working directory for the API jar, overrides the API defaults. The core and CLI modules contain no `api.*` settings.

Initial settings:

- `api.host`
- `api.port`
- `api.database.path`
- `api.database.username`
- `api.database.password`
- `api.sessionTimeoutMinutes`

## Implementation Conventions

API code and tests must follow the existing project style:

- indent Java and SQL with two spaces
- use records for immutable DTOs, configuration values, repository projections, and other data carriers where appropriate
- use classes where identity, mutable lifecycle, resource ownership, or substantial behavior makes a record unsuitable
- structure test names with the existing multiline `@DisplayName` Given/When/Then form
- keep Given, When, and Then phases visually distinct in each test
- prefer parameterized tests when multiple inputs or boundary cases verify the same behavior
- follow the existing JUnit conventions and avoid introducing another assertion or mocking framework without a concrete need

## Maven Modules And Distributions

Storyteller remains one Git project and one Maven reactor, split into three modules:

```text
Assistant/
  pom.xml                 parent and reactor aggregator
  storyteller-core/       shared domain, services, and core configuration
  storyteller-cli/        terminal adapter and CLI application
  storyteller-api/        HTTP adapter, database persistence, and API application
```

The `storyteller-core` module contains no CLI or API framework dependencies. `storyteller-cli` depends on the core and adds JLine. `storyteller-api` also depends on the core and adds Javalin, Jetty, and H2. The CLI and API modules do not depend on each other.

The CLI starts through `nl.llm.storyteller.cli.AssistantApp`. The API starts independently through `nl.llm.storyteller.api.ApiApplication`. Each application owns its lifecycle and distribution; no runtime discovery or `ServiceLoader` coupling is used.

This creates explicit application boundaries without introducing a compile-time dependency from the core to either adapter. No core class may depend on JLine, Javalin, H2, HTTP, cookie, terminal, or API DTO types.

## Package Boundary And Core Preservation

The future frontend API must live in a dedicated package so the current CLI and storyteller core remain usable without an HTTP server.

Module and package layout:

```text
storyteller-core
  nl.llm.storyteller.core/  reusable storyteller configuration and composition root
storyteller-cli
  nl.llm.storyteller.cli/   terminal adapter and application entry point
storyteller-api
  nl.llm.storyteller.api/
    http/                   HTTP server bootstrap, routing, controllers, error mapping
    http/dto/               request and response DTOs only
    session/                API session lifecycle and cookie handling
    persistence/            repository contracts and JDBC implementations
    bundle/                 import and download bundle handling
```

Dependency direction:

```text
CLI (`storyteller-cli`) ─────────> storyteller core
API (`storyteller-api`) ─────────> storyteller core
storyteller core ────────────────X API implementation
```

Rules for the API package:

- `api` may depend on the existing `core` packages, but core packages must not depend on `api` classes, HTTP types, cookies, or DTOs.
- Controllers translate HTTP requests into core calls and translate core results into DTOs; they must not rebuild prompts, validation, history mutation, derived-memory updates, or undo logic.
- API runtime state is persisted through repository contracts backed by JDBC; storage details must not leak into controllers or storyteller services.
- The API database must not replace the current CLI memory location or alter CLI behavior. The CLI may continue to use its existing file-backed stores behind the same storage boundaries.
- Browser-cookie handling stays in `api.session`; the cookie only identifies a server-owned session and never contains story content.
- Reusable session-facing operations should be extracted from existing services only when both the CLI and API need them. The extraction must preserve existing CLI behavior and test coverage.

The CLI and API have separate entry points and processes. Choosing the CLI distribution omits the API completely at compile and packaging time; choosing the API distribution omits the CLI and JLine completely.

## Session Model

The API should use explicit sessions.

A session represents:

- one active story conversation
- one effective storyteller configuration
- one set of prompt overrides
- one story history
- one summary state
- one recent-summary state
- one canonical state

The session should be identified by a server-generated `sessionId`.

The client should not be allowed to choose or reuse a live session identifier directly.
If an old session bundle is imported, the server should create a new live session from it.

### Browser Session Cookie

For a browser frontend, the server should also set an opaque session cookie that points to the active `sessionId`.
This is a convenience mechanism for restoring the active story after a browser refresh; it is not a container for story state.

Recommended cookie behavior:

- cookie name: `storyteller_session`
- cookie value: a random, opaque session reference or signed session reference
- local default: host-only cookie for `localhost`, `Path=/`, `HttpOnly`, `SameSite=Lax`, and no `Secure` flag because the local API uses HTTP
- expiry: sliding `Max-Age` aligned with the configured session inactivity timeout; renew it after a valid session access
- infinite sessions: use a long-lived cookie while server-side inactivity expiration is disabled; the database remains authoritative
- server-side data: history, summaries, canonical state, prompt overrides, and metadata remain in the API database
- browser behavior: a frontend can call a current-session endpoint without persisting a session id in JavaScript storage
- expired session: `GET /v1/session` returns `410` and clears the cookie; a missing cookie returns `404`
- deletion: clear the cookie when it points to the deleted session

For a future Docker or remote deployment, TLS should terminate at a reverse proxy. That deployment configures the API to emit the `Secure` cookie flag. The API should not infer that policy from arbitrary forwarded headers; only an explicitly trusted proxy integration may supply the external HTTPS state.

This remains compatible with non-browser API clients: they can keep using explicit `{sessionId}` paths and do not need a cookie.

## Confirmed Session Lifecycle

The first API deployment uses an embedded, file-backed H2 database for runtime state.
Sessions are ephemeral by default and remain available while they are actively used.

Confirmed behavior:

- each session has `createdAt`, `updatedAt`, `lastAccessedAt`, and `expiresAt`
- inactivity extends or refreshes `expiresAt`
- a user can explicitly make a session infinite, which disables inactivity cleanup until normal expiration is restored
- the default inactivity expiry is `60` minutes and should be configurable by the local operator
- expired sessions are cleaned up by a background job and again during application startup
- cleanup deletes the session and all owned runtime state in one transaction
- explicit session deletion performs the same transactional deletion immediately
- separately downloaded session bundles are not affected by session cleanup

This protects storage while keeping a local browser frontend usable through refreshes and short interruptions.

## Storage Strategy

The first API version should persist runtime state in an embedded, file-backed H2 database.

Database-backed runtime state includes:

- sessions and lifecycle metadata
- ordered messages and history cursors
- summary and recent-summary state
- canonical state and knowledge-graph state
- validated session configuration and prompt overrides

Static application defaults, bundled prompt resources, model files, and explicitly generated story or session-bundle downloads remain files. Large binary inputs may also remain files, with only their metadata and stable reference stored in the database.

Persistence rules:

- access runtime state only through repository contracts
- use JDBC and standard SQL syntax
- do not use H2-specific SQL, compatibility modes, data types, functions, identity behavior, or other vendor extensions
- keep transaction boundaries in the application service layer so a turn and its related state changes commit atomically
- store structured payloads as portable text when a normalized relational representation is not useful; do not depend on vendor-specific JSON column types
- keep the initial schema in an explicit, portable SQL resource
- test repository behavior independently from the existing CLI file stores

### Initial Database Schema

Identifiers are generated by the application. Timestamps are written and read as UTC. Large textual values use bounded `VARCHAR` columns so the schema does not depend on a vendor-specific `TEXT`, `CLOB`, or JSON type. The application must enforce smaller practical limits before persistence.

```sql
CREATE TABLE story_session (
  session_id VARCHAR(36) NOT NULL,
  title VARCHAR(255),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  last_accessed_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  infinite BOOLEAN DEFAULT FALSE NOT NULL,
  PRIMARY KEY (session_id)
);

CREATE TABLE session_configuration (
  session_id VARCHAR(36) NOT NULL,
  temperature DECIMAL(4, 3),
  top_p DECIMAL(4, 3),
  validation_enabled BOOLEAN,
  cache_buster_interval INTEGER,
  PRIMARY KEY (session_id),
  FOREIGN KEY (session_id) REFERENCES story_session (session_id) ON DELETE CASCADE
);

CREATE TABLE session_prompt_override (
  session_id VARCHAR(36) NOT NULL,
  override_name VARCHAR(64) NOT NULL,
  override_content VARCHAR(1000000) NOT NULL,
  PRIMARY KEY (session_id, override_name),
  FOREIGN KEY (session_id) REFERENCES story_session (session_id) ON DELETE CASCADE
);

CREATE TABLE story_message (
  session_id VARCHAR(36) NOT NULL,
  message_index INTEGER NOT NULL,
  message_role VARCHAR(16) NOT NULL,
  content VARCHAR(1000000) NOT NULL,
  PRIMARY KEY (session_id, message_index),
  FOREIGN KEY (session_id) REFERENCES story_session (session_id) ON DELETE CASCADE,
  CHECK (message_index >= 0),
  CHECK (message_role IN ('system', 'user', 'assistant', 'tool'))
);

CREATE TABLE session_memory (
  session_id VARCHAR(36) NOT NULL,
  summary_content VARCHAR(1000000),
  recent_summary_content VARCHAR(1000000),
  canonical_state_content VARCHAR(1000000),
  summary_cursor INTEGER NOT NULL,
  recent_summary_cursor INTEGER NOT NULL,
  canonical_state_cursor INTEGER NOT NULL,
  PRIMARY KEY (session_id),
  FOREIGN KEY (session_id) REFERENCES story_session (session_id) ON DELETE CASCADE,
  CHECK (summary_cursor >= 0),
  CHECK (recent_summary_cursor >= 0),
  CHECK (canonical_state_cursor >= 0)
);

CREATE TABLE turn_state (
  session_id VARCHAR(36) NOT NULL,
  trigger_word VARCHAR(255) NOT NULL,
  started BOOLEAN NOT NULL,
  round_number INTEGER NOT NULL,
  PRIMARY KEY (session_id),
  FOREIGN KEY (session_id) REFERENCES story_session (session_id) ON DELETE CASCADE,
  CHECK (round_number >= 0)
);

CREATE TABLE turn_protagonist (
  session_id VARCHAR(36) NOT NULL,
  protagonist_index INTEGER NOT NULL,
  protagonist_name VARCHAR(255) NOT NULL,
  turns_this_round INTEGER NOT NULL,
  PRIMARY KEY (session_id, protagonist_index),
  FOREIGN KEY (session_id) REFERENCES turn_state (session_id) ON DELETE CASCADE,
  CHECK (protagonist_index >= 0),
  CHECK (turns_this_round >= 0)
);

CREATE TABLE knowledge_graph (
  session_id VARCHAR(36) NOT NULL,
  schema_version INTEGER NOT NULL,
  revision BIGINT NOT NULL,
  PRIMARY KEY (session_id),
  FOREIGN KEY (session_id) REFERENCES story_session (session_id) ON DELETE CASCADE,
  CHECK (schema_version > 0),
  CHECK (revision >= 0)
);

CREATE TABLE knowledge_entity (
  session_id VARCHAR(36) NOT NULL,
  entity_id VARCHAR(255) NOT NULL,
  entity_type VARCHAR(64) NOT NULL,
  entity_name VARCHAR(255) NOT NULL,
  entity_source VARCHAR(64) NOT NULL,
  PRIMARY KEY (session_id, entity_id),
  FOREIGN KEY (session_id) REFERENCES knowledge_graph (session_id) ON DELETE CASCADE
);

CREATE TABLE knowledge_entity_alias (
  session_id VARCHAR(36) NOT NULL,
  entity_id VARCHAR(255) NOT NULL,
  alias_index INTEGER NOT NULL,
  alias_name VARCHAR(255) NOT NULL,
  PRIMARY KEY (session_id, entity_id, alias_index),
  FOREIGN KEY (session_id, entity_id)
    REFERENCES knowledge_entity (session_id, entity_id) ON DELETE CASCADE,
  CHECK (alias_index >= 0)
);

CREATE TABLE knowledge_fact (
  session_id VARCHAR(36) NOT NULL,
  fact_id VARCHAR(255) NOT NULL,
  subject_entity_id VARCHAR(255) NOT NULL,
  predicate_id VARCHAR(255) NOT NULL,
  object_entity_id VARCHAR(255) NOT NULL,
  polarity VARCHAR(32) NOT NULL,
  fact_status VARCHAR(32) NOT NULL,
  fact_source VARCHAR(64) NOT NULL,
  source_turn INTEGER,
  is_hard BOOLEAN NOT NULL,
  PRIMARY KEY (session_id, fact_id),
  FOREIGN KEY (session_id) REFERENCES knowledge_graph (session_id) ON DELETE CASCADE,
  FOREIGN KEY (session_id, subject_entity_id)
    REFERENCES knowledge_entity (session_id, entity_id),
  FOREIGN KEY (session_id, object_entity_id)
    REFERENCES knowledge_entity (session_id, entity_id),
  CHECK (source_turn IS NULL OR source_turn >= 0)
);
```

One transaction persists a completed turn: append its messages, update all affected cursors and derived state, update the knowledge graph when applicable, and finally update the session timestamps. Undo and session deletion are transactional for the same reason. Repository code must issue explicit statements and must not rely on database-generated identifiers.

## Future Database Direction

H2 is the embedded implementation for the first API version, not part of the domain contract. A later move to a production database such as PostgreSQL should require a datasource and deployment change, plus any deliberately database-specific migration, rather than changes to controllers or storyteller behavior.

Schema and query design must therefore remain portable from the start. Database-specific optimizations may be introduced only when a concrete production requirement justifies them and must stay isolated inside the persistence adapter.

## Session Bundle Import And Download

Live sessions and downloadable session bundles should be treated as different concepts.

Recommended distinction:

- story export: a readable markdown export of the story
- session bundle: a complete portable snapshot required to continue later

A session bundle should contain everything needed to continue a story on a later day in a new live session.

The first implemented bundle format contains:

- optional `manifest.json` with bundle version and story title; API exports always include it
- required `history.json`, including the three CLI history cursors
- optional `summary.md`
- optional `recent-summary.md`
- optional `canonical-state.yaml`
- optional `turn-state.json`; export always includes the current turn state
- optional `knowledge-graph.json`; export always includes the current graph, including an empty graph

The server rejects unknown paths, duplicate entries, incomplete message pairs, invalid cursors, invalid graph data,
archives larger than 32 MB, and archives that expand beyond 64 MB. Import validates the complete archive before opening
one database transaction. It creates a new live session with a new server-generated `sessionId`; it never replaces the
current or an existing session. A valid manifest title becomes the initial title; bundles without a manifest use the
source ZIP filename. The imported session starts with the normal inactivity timeout and can subsequently be made infinite.

The server-rendered routes are `POST /import` for multipart upload and `GET /export` for downloading the active session.
Future JSON API equivalents may use the resource-oriented paths documented below. Session configuration and prompt
override snapshots can be added to a later bundle-format version once those values are mutable through the API.

## Confirmed Configuration Model

The API should not accept unrestricted raw application configuration input.

Instead, it should allow a limited set of whitelisted overrides.

Likely first allowed overrides:

- `temperature`
- `topP`
- `validationEnabled`
- `cacheBusterInterval`
- `systemPrompt`
- `rules`
- `fixedProtagonists`

Each override must be server-side validated and normalized.

Examples:

- `temperature`: bounded to a safe range such as `0.0` to `2.0`
- `topP`: bounded to `0.0` to `1.0`
- prompt text fields: bounded by length
- prompt override files: bounded by size

The effective runtime configuration remains server-owned.

## Transport Expectations

The main performance optimization is not compression alone.
The main optimization is that session state stays on the server.

That means:

- the client does not keep sending history
- the client does not keep sending summary state
- the client does not keep sending canonical state
- the client usually does not keep resending prompt overrides

After that, the API can still support:

- gzip compression
- HTTP/2
- response streaming

Those are useful, but they are secondary to proper session ownership.

## API Style

The API should be session-oriented rather than raw prompt-oriented.

This keeps payloads small and the frontend simple.

Suggested top-level resource:

- `/v1/sessions`

## Endpoint Overview

Below is the first recommended endpoint set.
These are not final payload schemas yet, but they describe the intended contract and purpose.

### `POST /v1/sessions`

Creates a new live story session.

Why it is needed:

- the server must allocate a new session identity
- prompt overrides should be attached once at session creation
- the server should validate and normalize allowed overrides

Typical request responsibilities:

- optional session label or title
- optional whitelisted overrides
- optional imported session bundle reference in a later version

Typical response:

- `sessionId`
- effective config snapshot safe for UI display
- expiration metadata
- `Set-Cookie` for browser clients, pointing to the new active session

### `GET /v1/session`

Returns the current live session resolved from the browser session cookie.

Why it is needed:

- frontend startup and browser refresh do not need a session id stored in JavaScript
- the frontend can distinguish an active session, an expired session, and no session

Typical response:

- the same metadata and effective config view as `GET /v1/sessions/{sessionId}`

Recommended failure behavior:

- `404` when no session cookie is present
- `410` when the cookie refers to an expired or deleted session, while also clearing the cookie

### `GET /v1/sessions/{sessionId}`

Returns metadata for one live session.

Why it is needed:

- frontend refresh
- session resume
- session validity check
- display of expiration and effective settings

Typical response:

- session metadata
- effective whitelisted config view
- timestamps
- expiration status

### `POST /v1/sessions/{sessionId}/turns`

Submits one new user turn and returns the assistant response.

Why it is needed:

- this is the main storyteller action endpoint
- it keeps the request payload minimal
- it allows the server to update history, summaries, and canonical state internally

Typical request:

- JSON object `{ "prompt": "..." }`
- `prompt` is required, must contain non-whitespace text, and is bounded by the application service before the model is called
- no per-turn configuration or prompt overrides in the first implementation

Typical response:

- JSON object containing `sessionId`, `userMessageIndex`, `assistantMessageIndex`, and `response`
- the complete assistant response after normal response sanitization and, when enabled, validation

The first implementation loads recent messages for the selected session from `story_message`, combines them with the
server-owned core prompt resources, calls the configured OpenAI-compatible backend, and stores the completed user and
assistant pair in one database transaction. A failed backend call does not append either message. Session prompt
inspection and override endpoints are deliberately deferred; the endpoint uses the server's effective core prompts.

Summary, canonical-state, knowledge-graph, reset, and full CLI undo-and-retry parity remain subsequent API slices. Their absence must not
cause this first turn endpoint to read or write the CLI's file-backed runtime state.

The initial server-rendered interface is delivered by the same API application but isolated under `api.web`. Its story
workspace shows prompts and responses side by side above a full-width input area. The layout remains responsive across
desktop, tablet, and mobile viewports in both portrait and landscape orientations; narrow portrait screens stack each
prompt above its response. HTML controllers call the same application services as the JSON controllers and never call
the server's own HTTP API. Its initial Undo action atomically removes the latest complete user and assistant pair from
`story_message`; restoring the removed prompt for editing and reconciling future derived-memory state belong to the
full undo-and-retry API slice.

### `POST /v1/sessions/{sessionId}/reset`

Triggers the same conceptual reset action as the CLI reset shortcut.

Why it is needed:

- the model can drift
- the frontend needs an explicit API for reasserting rules and session discipline

Typical response:

- `accepted: true`
- `cacheBusterApplied: true`

Required behavior:

- send the transient reset-with-cache-buster request
- do not append the reset request or generated response to story history
- do not return a generated assistant acknowledgement as a conversation message

### `GET /v1/sessions/{sessionId}/history`

Returns session history for display.

Why it is needed:

- the frontend needs to render the conversation
- history should be fetched on demand rather than returned fully on every turn

Recommended behavior:

- support paging or a `sinceTurn` option later
- first version may return the full stored history for that session

### `GET /v1/sessions/{sessionId}/state`

Returns the currently derived state for the session.

Why it is needed:

- the frontend should be able to render canonical state
- summaries and state should be visible without recomputing them client-side

Typical response:

- canonical state
- full summary
- recent summary
- metadata showing how fresh they are

### `PATCH /v1/sessions/{sessionId}/config`

Updates allowed session overrides.

Why it is needed:

- the frontend may allow safe edits without creating a brand new session
- the server remains the validator and owner of the effective config

Typical allowed fields:

- `temperature`
- `topP`
- `validationEnabled`
- `cacheBusterInterval` (`0` disables periodic cache-buster requests)
- prompt override fields if session mutation is allowed

### `POST /v1/sessions/{sessionId}/undo`

Removes the latest persisted user+assistant turn and returns the removed user input for editing and retrying.

Why it is needed:

- the CLI already exposes this as its undo-and-retry action
- the server must clamp derived-memory cursors after the history changes
- the frontend needs the original input without rebuilding undo semantics client-side

Required behavior:

- remove the latest persisted user+assistant turn
- clamp summary, recent-summary, and canonical-state cursors to the shortened history
- execute the same transient reset-with-cache-buster request used by the CLI, regardless of `cacheBusterInterval`
- do not persist the reset request or its generated response as a story turn

Typical response:

- `restoredUserInput`
- `undone: true` or `false` when no persisted turn exists
- lightweight state freshness metadata

### `POST /v1/sessions/{sessionId}/exports/story`

Creates a readable story export.

Why it is needed:

- the current CLI already supports story export concepts
- the frontend should be able to request markdown output

Typical request options:

- `intro`
- `clean`
- `all`

Typical response:

- exported markdown
- or a file handle / download descriptor

### `POST /v1/sessions/{sessionId}/exports/bundle`

Creates a complete downloadable session bundle.

Why it is needed:

- users should be able to stop, download, clean up, and continue later
- this is different from story export because it must preserve working state

Typical response:

- bundle metadata
- bundle file location or download payload

### `POST /v1/session-imports`

Creates a new live session from a previously downloaded bundle.

Why it is needed:

- the previous live session may no longer exist
- import should create a fresh live session with its own lifecycle

Typical response:

- new `sessionId`
- imported metadata summary
- effective config summary
- `Set-Cookie` for browser clients, pointing to the imported live session

### `DELETE /v1/sessions/{sessionId}`

Deletes a live session explicitly.

Why it is needed:

- users may want immediate cleanup
- useful for frontend controls and testing

Recommended behavior:

- delete live session data
- leave separately downloaded bundles untouched
- clear the browser session cookie when it points to the deleted session

## Optional Endpoints Later

These are intentionally not in the minimum first version, but are likely useful later:

- `GET /v1/sessions`
- `GET /v1/models`
- `GET /v1/health`
- `GET /v1/config/schema`
- `POST /v1/sessions/{sessionId}/continue`
- `POST /v1/sessions/{sessionId}/commands/export`

## Why A Session API Is Better Than A Stateless Prompt API

A purely stateless prompt API would force the client to keep resending too much data:

- conversation history
- summary state
- canonical state
- active rules
- prompt overrides

That would create:

- larger payloads
- more duplicated logic in clients
- weaker consistency
- more complicated frontend state management

The session-based API is better aligned with how Storyteller already works internally.

## Relationship To The CLI

The CLI should remain a supported client.

Preferred long-term direction:

- storyteller core logic stays in services
- the CLI uses the same underlying services
- the API layer becomes another entry point
- a future frontend becomes another client

This avoids duplicating orchestration logic across interfaces.

### CLI And Frontend Behavioral Parity

The frontend must preserve the same storyteller and session behavior as the CLI. The presentation may differ, but the API must delegate to the same core operations and must not reimplement their semantics in JavaScript or controllers.

| CLI behavior                          | Frontend/API equivalent                                                | Required parity                                                                                               |
|---------------------------------------|------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| Send story input or `Ctrl-G` continue | `POST /v1/sessions/{sessionId}/turns`                                  | Same prompt assembly, validation, persistence, derived-memory scheduling, and periodic cache-buster behavior. |
| `Ctrl-W` reset                        | `POST /v1/sessions/{sessionId}/reset`                                  | Same transient reset-with-cache-buster; no story-history mutation and no assistant conversation message.      |
| `Ctrl-U` undo-and-retry               | `POST /v1/sessions/{sessionId}/undo`                                   | Same turn removal, cursor clamping, mandatory cache-buster reset, and restored user input.                    |
| `Ctrl-L` last turn                    | `GET /v1/sessions/{sessionId}/history` or a later last-turn projection | Same latest persisted user+assistant pair, without model invocation.                                          |
| `/export` variants                    | `POST /v1/sessions/{sessionId}/exports/story`                          | Same export modes and generated content.                                                                      |
| Local configuration                   | session create/config endpoints                                        | Same validated, whitelisted effective configuration.                                                          |

Any future CLI behavior that changes history, model state, memory state, or configuration must receive an API equivalent in the same change, unless it is deliberately marked CLI-only in the design document.

## Minimal First API Slice

The first local API should stay small and use the decisions already made in this document:

- persist API runtime state in the embedded H2 database through repository contracts
- create and restore the active session with the browser cookie
- support turns, reset, undo, history, state, and the whitelisted session configuration
- return the complete history for the first version; add paging only when a real session size requires it
- return story exports as direct Markdown responses; add downloadable file handles only when needed
- treat the documented undo result and derived-memory freshness metadata as the initial contract

Session listing, model inspection, health endpoints, and pagination remain later additions. They are not prerequisites for the initial local frontend.

The next implementation step is to convert this minimal slice into:

- an OpenAPI or Swagger contract
- request and response DTOs
- repository contracts and the H2/JDBC persistence adapter
- an initial portable SQL schema
- a first HTTP controller layer
