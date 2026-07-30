# Storyteller API Design

## Purpose

This document captures the current API design decisions for turning the Storyteller CLI into a local or remote API-backed application.
It is intentionally written before the OpenAPI or Swagger specification so the design decisions stay readable and easy to revise.

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
- a database dependency
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

## Session Lifetime

Sessions should be ephemeral by default.

Recommended behavior:

- each session has `createdAt`, `updatedAt`, `lastAccessedAt`, and `expiresAt`
- inactivity extends or refreshes `expiresAt`
- expired sessions are cleaned up by a background job or startup cleanup step
- cleanup removes the on-disk session directory

Recommended first default:

- expire after `30` to `60` minutes of inactivity

This protects disk space while still allowing refreshes, reopen actions, and short interruptions.

## Storage Strategy

The first API version should use on-disk session storage rather than a database.

Recommended layout:

```text
./memory/sessions/<session-id>/
  history.json
  history.md
  summary.md
  recent-summary.md
  canonical-state.yml
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

But the first API version does not need that complexity if session state is already server-owned and persisted on disk.

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
- `canonical-state.yml`
- `session-config.json`
- prompt override snapshots when present
- effective rules snapshot when present
- effective fixed protagonists snapshot when present

Recommended behavior:

- download bundle from a live session
- cleanup can remove the live session later
- import bundle creates a new live session with a new `sessionId`

## Configuration Model

The API should not accept unrestricted raw application configuration input.

Instead, it should allow a limited set of whitelisted overrides.

Likely first allowed overrides:

- `temperature`
- `topP`
- `validationEnabled`
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

- confirmation
- optional resulting assistant acknowledgement if the reset is modeled as a generated turn

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
- prompt override fields if session mutation is allowed

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

### `DELETE /v1/sessions/{sessionId}`

Deletes a live session explicitly.

Why it is needed:

- users may want immediate cleanup
- useful for frontend controls and testing

Recommended behavior:

- delete live session data
- leave separately downloaded bundles untouched

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

## Recommended Next Steps

Before writing the OpenAPI spec:

1. confirm the session lifecycle rules
2. confirm the whitelist of allowed config overrides
3. confirm the on-disk session directory layout
4. confirm whether reset is modeled as a command or a synthetic turn
5. confirm whether exports return raw content or downloadable file handles
6. confirm whether history should be paged from the first version onward

After those points are stable, this design should be converted into:

- an OpenAPI or Swagger contract
- request and response DTOs
- a session storage abstraction
- a first HTTP controller layer
