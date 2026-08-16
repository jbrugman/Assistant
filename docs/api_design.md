# Storyteller API Design

## Purpose

This document captures the current API design decisions for turning the Storyteller CLI into a local or remote API-backed application.
It is intentionally written before the OpenAPI or Swagger specification so the design decisions stay readable and easy to revise.

The accompanying component view is available in [`docs/architecture/03-storyteller-api-design.puml`](architecture/03-storyteller-api-design.puml).

The main goals are:

- keep the existing CLI viable
- make a frontend possible without duplicating storyteller logic
- avoid sending large prompt, history, and state payloads on every request
- keep the first API version simple enough to build without introducing a database immediately

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
- a database dependency for the local API deployment
- a full Swagger or OpenAPI contract in this phase

## Backend Assumption

The storyteller backend remains an OpenAI-compatible client.

The API server is not responsible for running models directly.
It should continue to work with:

- LM Studio
- Jan
- Ollama through a compatible API layer
- hosted OpenAI-compatible APIs
- other local or remote compatible backends

## Modular Monolith Today, Split-Friendly Tomorrow

The application is intentionally a single deployable Maven project and jar for now. 
The local storyteller benefits from a simple startup, one configuration surface, and no network boundary between its adapters and story behavior.

It is nevertheless organized as a modular monolith: `core` owns storyteller behavior, while `cli` and the future `api` are adapters with one-way dependencies toward that core. 
No core class may depend on HTTP, cookie, terminal, or DTO types. 
If separate deployment becomes justified later, the adapters and their dependencies can be extracted into separate modules or services without first untangling domain behavior.

## Package Boundary And Core Preservation

The future frontend API must live in a dedicated package so the current CLI and storyteller core remain usable without an HTTP server.

Recommended package layout:

```text
nl.llm.storyteller
  core/                     reusable storyteller configuration and composition root
    service/                storyteller core and orchestration
    model/                  core records and value types
  cli/                      terminal adapter
  api/
    http/                   HTTP server bootstrap, routing, controllers, error mapping
    dto/                    request and response DTOs only
    session/                API session lifecycle, cookie handling, and disk-session storage
    bundle/                 import and download bundle handling
```

Dependency direction:

```text
CLI (TerminalStoryteller) ─┐
                           ├─> storyteller core (`core` / `core.service` / `core.model`)
HTTP API (`api`) ──────────┘
```

Rules for the API package:

- `api` may depend on the existing `core` packages, but core packages must not depend on `api` classes, HTTP types, cookies, or DTOs.
- Controllers translate HTTP requests into core calls and translate core results into DTOs; they must not rebuild prompts, validation, history mutation, derived-memory updates, or undo logic.
- API session storage adapts the existing file-based core state to per-session directories; it must not replace the current CLI memory location or alter CLI behavior.
- Browser-cookie handling stays in `api.session`; the cookie only identifies a server-owned session and never contains story content.
- Reusable session-facing operations should be extracted from existing services only when both the CLI and API need them. The extraction must preserve existing CLI behavior and test coverage.

This lets the current CLI continue to construct and use its local core exactly as it does now, while the API becomes an additional entry point rather than a framework requirement.

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
- server-side data: history, summaries, canonical state, prompt overrides, and metadata remain in the session directory on disk
- browser behavior: a frontend can call a current-session endpoint without persisting a session id in JavaScript storage
- expired session: `GET /v1/session` returns `410` and clears the cookie; a missing cookie returns `404`
- deletion: clear the cookie when it points to the deleted session

For a future Docker or remote deployment, TLS should terminate at a reverse proxy. That deployment configures the API to emit the `Secure` cookie flag. The API should not infer that policy from arbitrary forwarded headers; only an explicitly trusted proxy integration may supply the external HTTPS state.

This remains compatible with non-browser API clients: they can keep using explicit `{sessionId}` paths and do not need a cookie.

## Confirmed Session Lifecycle

The first API deployment is a local, filesystem-backed system and does not require a database.
Sessions are ephemeral by default and remain available while they are actively used.

Confirmed behavior:

- each session has `createdAt`, `updatedAt`, `lastAccessedAt`, and `expiresAt`
- inactivity extends or refreshes `expiresAt`
- the default inactivity expiry is `60` minutes and should be configurable by the local operator
- expired sessions are cleaned up by a background job and again during application startup
- cleanup removes the on-disk session directory
- explicit session deletion removes the same directory immediately
- separately downloaded session bundles are not affected by session cleanup

This protects disk space while keeping a local browser frontend usable through refreshes and short interruptions.

## Storage Strategy

The first API version should use on-disk session storage rather than a database.

Recommended layout:

```text
./memory/sessions/<session-id>/
  history.json
  history.md
  summary.md
  recent-summary.md
  canonical-state.yaml
  session-config.json
  session-metadata.json
  prompt-overrides/
    systemprompt.md
    rules.md
    fixed_protagonists.yml
  exports/
```

Why this is the preferred first step:

- avoids large request payloads
- avoids immediate database complexity
- keeps sessions easy to inspect and debug
- maps well to the current file-based architecture
- can be replaced later by a database-backed store behind the same interface

## Future Storage Direction

A database may still make sense later.

Likely reasons to move there later:

- multiple simultaneous users
- session persistence across longer time spans
- querying and administration
- pagination over large histories
- deployment on a shared server

But the first local API version does not need that complexity because session state is server-owned and persisted on disk.

## Session Bundle Import And Download

Live sessions and downloadable session bundles should be treated as different concepts.

Recommended distinction:

- story export: a readable markdown export of the story
- session bundle: a complete portable snapshot required to continue later

A session bundle should contain everything needed to continue a story on a later day in a new live session.

Recommended contents:

- `manifest.json`
- `history.json`
- `summary.md`
- `recent-summary.md`
- `canonical-state.yaml`
- `session-config.json`
- prompt override snapshots when present
- effective rules snapshot when present
- effective fixed protagonists snapshot when present

Recommended behavior:

- download bundle from a live session
- cleanup can remove the live session later
- import bundle creates a new live session with a new `sessionId`

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

- user input text
- optional small per-turn overrides if explicitly supported

Typical response:

- assistant response
- message ids or turn indices
- optional lightweight state metadata

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

- use the documented filesystem session directory layout
- create and restore the active session with the browser cookie
- support turns, reset, undo, history, state, and the whitelisted session configuration
- return the complete history for the first version; add paging only when a real session size requires it
- return story exports as direct Markdown responses; add downloadable file handles only when needed
- treat the documented undo result and derived-memory freshness metadata as the initial contract

Session-bundle import/export, session listing, model inspection, health endpoints, and pagination remain later additions. They are not prerequisites for the initial local frontend.

The next implementation step is to convert this minimal slice into:

- an OpenAPI or Swagger contract
- request and response DTOs
- a session storage abstraction
- a first HTTP controller layer
