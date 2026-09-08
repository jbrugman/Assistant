# ADR-004: Render the default web interface server-side with JTE

Status: accepted  
Date: 2026-09-07

## Context

Storyteller has a default browser interface next to its CLI and JSON API. The bundled interface currently supports a
focused simple workflow: creating or importing a session, reading the conversation, submitting and undoing turns,
exporting the session, and stopping the session (removing the session from the system). It is one client of the API, not
the required interface for Storyteller. Other applications may provide their own web, desktop, mobile, or automated
interface against the JSON API.

Introducing a client-side application framework would add a separate JavaScript dependency tree, build pipeline,
application state model, and API client. That cost is not justified by the current interface. Rendering HTML manually in
Java would avoid those dependencies but would make escaping, reuse, and maintenance unnecessarily difficult.

The web interface must remain part of the independently deployable `storyteller-api` module. It must use the same
application services as the JSON controllers instead of reproducing story behavior in the browser or calling the
server's own HTTP API.

## Decision

The bundled default web interface uses server-side rendering. Javalin handles its HTTP routes and renders JTE templates
from the `storyteller-api` module. HTML controllers call application services directly and provide view models to the
templates. CSS and small, page-specific JavaScript enhancements remain static resources served by the same application
on the same host and port.

This decision applies only to the default interface shipped with Storyteller. It does not constrain independent clients:
the JSON API remains the public integration boundary and may be used by any frontend technology.

JTE templates are compiled during the Maven build and the API uses JTE's precompiled template engine at runtime.
Generated JTE sources and classes are build artifacts and must not be committed to Git.

## Rationale

JTE was selected because it:

- integrates directly with Javalin;
- compiles templates to Java, exposing template and parameter errors during the build;
- escapes HTML output according to the configured HTML content type;
- requires no Node.js, npm, or separate frontend build;
- keeps templates, static resources, and deployment inside `storyteller-api`;
- supports precompiled templates and GraalVM Native Image through the JTE native-resources extension.

Server-side rendering also keeps browser state small. Session state remains authoritative in H2 and the browser uses an
opaque session cookie. Regular form submissions remain the baseline behavior; JavaScript is used only where it improves
interaction, such as layout controls, duplicate-submit prevention, and incremental history loading.

## Benefits

- The browser receives complete HTML and can render the page immediately without first downloading, parsing, and
  executing a frontend application bundle.
- Initial rendering is simple and fast, with little client-side CPU and memory overhead.
- The interface works without a client-side application bootstrap or hydration step.
- Navigation and form handling use standard browser behavior, while JavaScript remains a targeted enhancement.
- There is one application to build, start, package, configure, and deploy.
- Server-owned session state does not have to be duplicated in a client-side application state model.
- Alternative clients remain free to use any rendering technology because JTE is an implementation detail of the
  bundled interface, not part of the JSON API contract.

## Alternatives considered

### Angular, React, or another single-page application

Not selected for the initial interface. A SPA may become appropriate if the browser client develops substantial
independent state, complex interactive editing, or a need for a separately deployed frontend. It currently adds more
framework and build complexity than the interface requires.

### Manually generated HTML

Not selected. Building HTML strings in controllers would mix presentation with request handling and make safe escaping
and template maintenance harder.

### A larger server-side web framework

Not selected. Frameworks providing dependency injection, ORM, or a complete MVC stack conflict with the deliberate use
of Javalin as a small, explicit HTTP adapter.

## Consequences

- Browser pages and the JSON API are delivered by the same API application and port.
- The bundled web interface is the default browser client alongside the CLI; it is not the only supported client.
- The web layer remains under `nl.llm.storyteller.api.web`; domain and orchestration behavior remain outside templates
  and controllers.
- Page rendering normally causes a server round trip, while targeted JavaScript may progressively enhance interaction.
- JTE templates and static assets are packaged with the API distribution and included in native-image builds.
- A separate frontend framework can still be introduced later, but that requires a new architectural decision based on
  concrete interaction or deployment requirements.
