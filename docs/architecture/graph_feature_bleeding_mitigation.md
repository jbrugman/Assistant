# Graph-based mitigation of entity contagion and feature bleeding

Status: accepted; implementation in progress  
Scope: Storyteller chat generation, validation and canonical memory  
Last updated: 2026-08-25

Implementation note: version 1.2.0 implements the graph domain model, a configurable predicate catalog that is closed after startup, validation, immutable indexes and atomic JSON store, plus a minimal runtime slice. Predicate type constraints, positive/negative rendering, and `/graph -fill` instructions all use the same catalog. The application automatically loads the configured graph, injects relevant active hard facts into generation and validation prompts, provides local `/graph` inspection and empty `/graph -generate` initialization, and supports model-assisted `/graph -fill` extraction from only the fixed-protagonist document. Turn-driven mutation is not implemented.

## 1. Context

Storyteller currently uses fixed protagonist constraints, summaries, canonical state and recent turns to provide continuity. These sources are primarily unstructured text. As a story grows, a language model can accidentally transfer a characteristic, skill, possession, relationship or history from one entity to another.

Two related failure modes are in scope:

- **Entity contagion:** an object, relation or event associated with one entity is attributed to another entity.
- **Feature bleeding:** a property, ability, role or limitation of one character is attributed to another character.

Example:

- Chris is a guitarist and owns a Fender Stratocaster.
- Mike is Chris's friend and cannot play guitar.
- A generated response states that Mike plays a solo on his Fender.

The existing validator can return `ALLOW` or a minimally corrected `REPLACE` response, but it does not yet receive a deterministic, entity-scoped representation of canonical facts. Unstructured canonical state alone does not provide sufficiently precise retrieval or contradiction detection.

## 2. Goals

The solution should:

1. Reduce entity contagion and feature bleeding during generation.
2. Detect explicit contradictions before a response is committed to history.
3. Allow the validator to return a minimally corrected response.
4. Distinguish explicitly false facts from unknown facts.
5. Keep canonical facts human-readable and Git-friendly on disk.
6. Keep runtime lookups fast and local to the Java process.
7. Avoid introducing an external graph database or service.
8. Preserve current behavior when the graph feature is disabled or no relevant facts exist.
9. Introduce the feature incrementally, with measurable behavior at each phase.

## 3. Non-goals

The first (and probably most later) implementations will not:

- model every sentence or event as a graph;
- perform arbitrary graph analytics or multi-hop reasoning;
- infer that a fact is false merely because it is absent;
- automatically promote every LLM-extracted claim to hard canon;
- replace recent turns, summaries or canonical prose;
- use the graph to perform direct string rewriting in Java;
- require Neo4j, JanusGraph, TinkerPop or another external runtime;
- solve general pronoun and coreference resolution deterministically.

## 4. Architectural decisions

### ADR-001: Use JSON as the canonical on-disk format

**Decision:** Store the lightweight knowledge graph as versioned JSON.

**Rationale:**

- Jackson JSON support is already present.
- JSON is strict, portable and straightforward to validate.
- No YAML dependency or YAML-specific parsing behavior is required.
- Atomic replacement and deterministic serialization are simple.
- The file remains readable and editable by humans.

YAML may be added later as an import/export format. It is not the canonical persistence format for the first version.

### ADR-002: Use purpose-built in-memory indexes

**Decision:** Load the JSON document into immutable domain objects and create application-specific indexes using Java collections.

The initial indexes are expected to include:

```java
Map<EntityId, Entity> entitiesById;
Map<String, Set<EntityId>> entitiesByNormalizedAlias;
Map<EntityId, List<Fact>> factsBySubject;
Map<EntityId, List<Fact>> factsByObject;
Map<FactKey, TruthValue> truthByKey;
```

**Rationale:** The required operations are direct fact lookup, alias resolution and traversal over at most one relation. JGraphT does not materially simplify these operations. TinkerPop and JanusGraph would add disproportionate complexity and dependencies.

JGraphT can be reconsidered if future requirements include graph algorithms or substantial multi-hop traversal. JanusGraph should only be reconsidered if graph size and persistence requirements grow far beyond an in-process story graph.

### ADR-003: Model truth explicitly

**Decision:** A fact must distinguish positive, negative and unknown truth states.

Absence of a fact means `UNKNOWN`, it does not mean `FALSE`.

Negation is represented through polarity rather than inverse predicate names:

```text
Mike -- CAN_PLAY / NEGATIVE --> guitar
```

instead of:

```text
Mike -- CANNOT_PLAY --> guitar
```

This allows deterministic conflict detection between positive and negative assertions that share the same subject, predicate and object.

### ADR-004: Inject only relevant graph facts into prompts

**Decision:** Do not include the complete graph in every prompt. Resolve the entities relevant to the current turn and inject a compact fact bundle.

For chat generation, relevant entities are selected from:

- the current user input;
- active entities from recent scene context;
- exact entity names and configured aliases;
- direct neighbours, limited to one graph edge, when required to explain a hard fact.

For validation, entity selection is performed again over:

- the current user input;
- the generated draft response;
- active entities from recent scene context.

The second retrieval is necessary because the draft can introduce an entity or object that was not present in the user input.

### ADR-005: Use the graph for both prevention and correction

**Decision:** Relevant hard facts are supplied to both the chat model and the validator.

- Chat injection reduces the probability of generating a contradiction.
- Validator injection detects remaining contradictions and supports minimal correction.

The existing `ALLOW` and `REPLACE` validation contract remains in place. Java identifies and presents relevant facts; the LLM performs natural-language correction. Java does not attempt to rewrite story prose directly.

### ADR-006: Preserve recent turns initially

**Decision:** Do not lower the number of recent turns in the same change that introduces graph injection.

The graph answers **what is canonically true**. Recent turns answer **what is happening now**, including dialogue, tone, unfinished actions and pronoun references. The graph supplements but does not replace conversational context.

After graph injection has been measured independently, recent context may move from a fixed turn count to a token budget. Token budgets should be treated as configurable upper bounds, not targets that must be filled. Suitable values depend on the model architecture and size, quantization, runtime, configured context size, prompt caching, available memory and acceptable prefill latency.

Conservative ranges that may be useful as an initial evaluation set on local hardware are:

- relevant graph facts: up to 300-600 tokens;
- recent conversation: up to 1,500-2,500 tokens;
- summaries: up to 750-1,250 tokens.

The validator should not automatically receive the full recent-conversation budget. Its context should normally be limited to the validation instructions, relevant hard graph facts, current user input, draft response and only the additional scene context needed to interpret references or ambiguity.

These ranges are hypotheses for comparison, not recommended universal defaults. Final defaults should be based on measurements of prefill latency, memory pressure, continuity quality, missed contradictions and incorrect corrections for the supported model/runtime combinations. A higher budget may be appropriate when additional scene context materially improves correctness; a lower budget is preferable when retrieval already supplies the required facts.

### ADR-007: Separate hard canon from proposed facts

**Decision:** Automatically extracted facts are not immediately trusted as hard canon.

Fact authority, in descending order, is:

1. explicit user/configuration facts;
2. user-approved corrections;
3. validated story facts;
4. LLM-derived proposed facts.

An automatically derived fact begins as `PROPOSED` or soft. It cannot silently replace or contradict an active hard fact. Conflicts must be retained for review or resolved through an explicit policy.

### ADR-008: Persist atomically and publish immutable snapshots

**Decision:** Updates are written to a temporary sibling file and atomically moved over the target where supported. After a successful write, a new immutable in-memory graph snapshot is published.

Readers always observe either the previous valid snapshot or the new valid snapshot. A malformed manually edited file must not partially replace the running graph.

### ADR-009: Initialize the graph from fixed protagonists

**Decision:** When no graph document exists, initialize it automatically from `fixed_protagonists`. On subsequent starts, load the existing graph rather than rebuilding it wholesale.

Facts originating in `fixed_protagonists` are hard facts with the highest source authority. When fixed protagonist definitions change, synchronize only facts owned by that source. Preserve facts learned later from other sources unless they conflict with a new hard protagonist fact.

This prevents a restart or configuration refresh from discarding user corrections, story events or manually curated graph content.

### ADR-010: Provide controlled `/generategraph` synchronization

**Decision:** Add a `/generategraph` control command that explicitly refreshes the graph from the available canonical sources.

The initial source set is:

1. fixed protagonists;
2. current canonical state;
3. current summary;
4. the most recent two complete user/assistant turns;
5. the existing graph, used as reconciliation input rather than textual evidence.

The number of recent turns should be configurable; two is an initial evaluation value rather than a universal default. Source provenance and authority must be retained because summaries and canonical state are derived and may be lossy or stale.

`/generategraph` performs a merge/synchronization. It does not infer that a missing fact has become false, and it does not silently remove existing facts that are absent from the supplied context. A future `/generategraph rebuild` mode may produce a complete candidate replacement, but it is outside the first delivery and must preserve or explicitly resolve manually curated facts.

### ADR-011: Separate probabilistic extraction from deterministic graph construction

**Decision:** Use an LLM only to translate free text into schema-constrained candidate operations. Java exclusively validates, normalizes, reconciles, indexes and persists those operations.

Free text cannot be converted into facts deterministically by Java alone. Conversely, an LLM must not directly write or replace the graph document. The boundary is structured output such as `UPSERT`, `CLOSE` or `SUPERSEDE` candidate operations with evidence and source provenance.

Java must deterministically reject:

- unknown predicates or entity types;
- invalid subject/object type combinations;
- missing or ambiguous entity references;
- unsupported operation types;
- contradictory active hard facts;
- attempts by lower-authority sources to overwrite fixed protagonist facts.

### ADR-012: Use a closed ontology and bounded entity creation

**Decision:** Configure supported entity types, predicates and their semantics in advance. The extractor cannot invent new predicates or entity types.

For the first version:

- `CHARACTER` subjects come from `fixed_protagonists`;
- `ITEM` and `SKILL` entities may be created as bounded objects of allowed predicates;
- unknown named characters are not automatically promoted to graph subjects;
- an object is created only when attached to a configured subject through an allowed predicate;
- each predicate defines permitted subject/object types and persistence behavior.

This keeps the graph focused while still allowing new possessions such as Valerie's microphone or keyboard to enter canon.

### ADR-013: Keep graph capabilities in a bounded core module

**Decision:** Implement the knowledge graph as a bounded module under `nl.llm.storyteller.core.graph`. Other application components access it through a small public service facade rather than depending directly on persistence, extraction or reconciliation classes.

The graph is core Storyteller domain capability. It is not a CLI concern, a prompt-builder concern or a feature of a specific LLM backend. The module owns:

- graph domain records and ontology types;
- the immutable in-memory snapshot and indexes;
- persistence and atomic publication;
- schema-constrained candidate extraction;
- deterministic validation and reconciliation;
- relevant-fact selection and formatting inputs.

The module must not depend on terminal rendering, `TerminalStoryteller`, `StorySessionService`, concrete llama.cpp/MLX clients or prompt assembly implementations. The CLI and story flow are adapters/consumers of the graph facade.

An initial package layout is:

```text
nl.llm.storyteller.core.graph
|- KnowledgeGraphService.java
|- KnowledgeGraphSnapshot.java
|- RelevantGraphContext.java
|- GraphGenerationResult.java
|  
|- model
|  |- Entity.java
|  |- EntityId.java
|  |- EntityType.java
|  |- Fact.java
|  |- FactKey.java
|  |- Predicate.java
|  |- Polarity.java
|  |- FactSource.java
|  |- FactStatus.java
|  `- KnowledgeGraphDocument.java
|  
|- persistence
|  `- KnowledgeGraphStore.java
|  
`- generation
   |- GraphFactExtractor.java
   |- GraphCandidateOperation.java
   |- GraphReconciler.java
   `- LlmGraphFactExtractor.java
```

This is a responsibility map, not a requirement to create every class or subpackage immediately. The first implementation should avoid empty abstractions and introduce only the types needed by the delivered slice.

The facade may initially expose:

```java
public interface KnowledgeGraphService {
  KnowledgeGraphSnapshot current();

  RelevantGraphContext relevantFacts(
    String userInput,
    String draftResponse
  );

  GraphGenerationResult generateGraph()
    throws IOException, InterruptedException;
}
```

If query and management responsibilities diverge materially, the facade may later be split into `KnowledgeGraphQueryService` and `KnowledgeGraphManagementService`. This split is not required for the first iteration.

LLM extraction is isolated behind a graph-owned port:

```java
public interface GraphFactExtractor {
  List<GraphCandidateOperation> extract(GraphExtractionInput input)
    throws IOException, InterruptedException;
}
```

The implementation may adapt the existing generic `ChatClient`, but deterministic graph components do not depend on an LLM client. `GraphReconciler`, domain validation, indexes and persistence must be testable using candidate operations alone.

The runtime publishes an immutable graph snapshot only after successful reconciliation and persistence. A replacement may be held through an `AtomicReference<KnowledgeGraphSnapshot>` or an equivalent single-publication mechanism. Readers therefore never observe a partially applied update.

## 5. Proposed data model

Initial JSON shape:

```json
{
  "schemaVersion": 1,
  "revision": 12,
  "entities": {
    "character.chris": {
      "type": "CHARACTER",
      "name": "Chris",
      "aliases": ["Christopher"]
    },
    "character.mike": {
      "type": "CHARACTER",
      "name": "Mike",
      "aliases": []
    },
    "item.chris_guitar": {
      "type": "ITEM",
      "name": "Chris' Fender Stratocaster",
      "aliases": ["de Stratocaster", "Chris' gitaar"]
    },
    "skill.guitar": {
      "type": "SKILL",
      "name": "gitaar spelen",
      "aliases": ["gitaarspelen"]
    }
  },
  "facts": [
    {
      "id": "fact-001",
      "subject": "character.chris",
      "predicate": "CAN_PLAY",
      "object": "skill.guitar",
      "polarity": "POSITIVE",
      "status": "ACTIVE",
      "source": "USER",
      "hard": true
    },
    {
      "id": "fact-002",
      "subject": "character.mike",
      "predicate": "CAN_PLAY",
      "object": "skill.guitar",
      "polarity": "NEGATIVE",
      "status": "ACTIVE",
      "source": "USER",
      "hard": true
    },
    {
      "id": "fact-003",
      "subject": "character.chris",
      "predicate": "POSSESSES",
      "object": "item.chris_guitar",
      "polarity": "POSITIVE",
      "status": "ACTIVE",
      "source": "STORY",
      "sourceTurn": 18,
      "hard": true
    }
  ]
}
```

Minimum domain concepts:

- `Entity`: stable ID, type, display name and aliases.
- `Fact`: stable ID, subject, predicate, object, polarity, status, source and hardness.
- `FactKey`: subject, predicate and object.
- `TruthValue`: `TRUE`, `FALSE`, or `UNKNOWN`.
- `FactStatus`: initially `ACTIVE`, `PROPOSED`, `RETRACTED`, or `SUPERSEDED`.
- `FactSource`: initially `USER`, `CONFIGURATION`, `STORY`, or `DERIVED`.

Temporal fields such as `validFromTurn` and `validToTurn` should be added when location and other temporary states are introduced. They need not block the first feature-bleeding implementation.

The first implemented ontology is deliberately restricted to the smallest useful vertical slice:

```text
Entity types:
- CHARACTER
- ITEM
- SKILL

Predicates:
- POSSESSES
- CAN_PERFORM
```

This slice covers ownership contagion and skill bleeding while avoiding premature decisions about role and relationship semantics. Entity types such as `ROLE` and predicates such as `HAS_ROLE`, `IS_FRIEND_OF` or `IS_PARENT_OF` are possible later extensions, not part of the first implementation.

The ontology is configuration, not model output. A schema entry defines at least:

```json
{
  "POSSESSES": {
    "subjectTypes": ["CHARACTER"],
    "objectTypes": ["ITEM"],
    "persistence": "DURABLE",
    "temporal": true,
    "allowDynamicObject": true
  },
  "CAN_PERFORM": {
    "subjectTypes": ["CHARACTER"],
    "objectTypes": ["SKILL"],
    "persistence": "DURABLE",
    "temporal": false,
    "allowDynamicObject": true
  }
}
```

Predicate metadata may later include directionality, symmetry, exclusivity and whether explicit contradictions are automatically correctable. New predicates should be introduced only for demonstrated story requirements.

## 6. Runtime components

Proposed component boundaries:

### `KnowledgeGraphStore`

- Reads and validates the JSON document.
- Writes updated documents atomically.
- Rejects missing entity references, duplicate IDs and contradictory active hard facts.
- Preserves the last valid runtime snapshot if loading a changed file fails.

### `InMemoryKnowledgeGraph`

- Owns immutable graph data and indexes.
- Resolves direct facts and truth values.
- Does not perform LLM calls or persistence.

### `EntityResolver`

- Normalizes names and aliases.
- Finds explicitly named entities in input text.
- Accepts active scene entities as supplemental context.
- Returns ambiguity rather than guessing when an alias maps to multiple entities.

### `RelevantFactSelector`

- Selects active, relevant facts for a set of entities.
- Prioritizes hard negative facts, exclusive ownership and character-specific constraints.
- Limits traversal depth and prompt size.
- Produces a stable, deterministic ordering.

### `GraphPromptFormatter`

- Formats facts as compact, unambiguous validator/chat context.
- Explicitly states that unknown does not mean false.
- Includes entity IDs when display names are ambiguous.

### `GraphConsistencyChecker`

- Detects contradictions between structured claims and graph facts when structured claims are available.
- Initially supplies deterministic evidence to the validator rather than attempting to understand arbitrary prose itself.
- Uses outcomes such as `NO_CONFLICT`, `POSSIBLE_CONFLICT` and `DEFINITE_CONFLICT`.

### `GraphFactExtractionClient`

- Sends the selected textual sources to an LLM using strict structured output.
- Requests candidate graph operations rather than a replacement graph document.
- Includes the configured ontology and known subject IDs in the extraction request.
- Returns candidates with evidence, source type and source turn where available.
- Does not mutate runtime or persisted state.

### `GraphReconciler`

- Validates candidate operations against the closed ontology.
- Applies source authority and provenance rules.
- Deduplicates facts by normalized `FactKey`.
- Distinguishes a legitimate state transition from a historical contradiction.
- Produces either a new valid immutable document or a set of rejected/conflicting candidates.

### Graph initialization and `/generategraph` flow

At startup:

```text
graph file absent
  -> extract fixed protagonist facts
  -> validate and reconcile in Java
  -> atomically persist initial graph

graph file present
  -> load and validate graph
  -> synchronize fixed-protagonist-owned facts
  -> preserve facts from other sources
```

On `/generategraph`:

```text
fixed protagonists + canonical state + summary + recent complete turns
                              |
                              v
                 schema-constrained LLM extraction
                              |
                              v
                    candidate operations
                              |
                              v
       Java validation + normalization + source reconciliation
                              |
                              v
                 candidate immutable graph
                              |
                  valid -----+----- invalid/conflicting
                    |                         |
                    v                         v
          atomic write and publish       retain current graph
                                        report rejected candidates
```

An extraction response may resemble:

```json
{
  "operations": [
    {
      "operation": "UPSERT",
      "subject": "character.valerie",
      "predicate": "POSSESSES",
      "object": {
        "id": "item.valerie_microphone",
        "type": "ITEM",
        "name": "Valerie's microphone"
      },
      "polarity": "POSITIVE",
      "evidence": {
        "source": "USER_TURN",
        "turn": 42
      }
    }
  ]
}
```

The accepted operation can create an item entity because `POSSESSES` permits dynamic `ITEM` objects. It cannot create a new predicate or promote an unknown character to a configured subject.

## 7. Prompt and validation flow

### Generation

```text
user input + active scene entities
        |
        v
EntityResolver
        |
        v
RelevantFactSelector --> compact generation fact bundle
        |                         |
        +-------------------------+
                                  v
rules + fixed protagonists + summaries + graph facts + recent turns + user input
                                  |
                                  v
                            draft response
```

### Validation

```text
user input + draft response + active scene entities
                     |
                     v
               EntityResolver
                     |
                     v
          RelevantFactSelector
                     |
                     v
rules + relevant hard facts + draft response
                     |
                     v
         ALLOW or minimal REPLACE
                     |
                     v
            committed history
```

Example validator context:

```text
RELEVANT CANON FACTS

- character.chris CAN_PLAY skill.guitar = TRUE [hard]
- character.mike CAN_PLAY skill.guitar = FALSE [hard]
- character.chris POSSESSES item.chris_guitar = TRUE [hard]

Interpretation rules:
- UNKNOWN is not FALSE.
- Touching or using an item does not by itself imply ownership.
- Replace only when the candidate explicitly contradicts a hard fact.
- Preserve the intended scene and change only the violating content.
```

If the draft says that Mike plays guitar, the validator receives a focused correction instruction. It may return a corrected full response using the existing `REPLACE` contract. The replacement, not the rejected draft, is appended to history.

## 8. Retrieval rules

Initial retrieval should be deliberately conservative:

1. Match exact normalized names and aliases.
2. Include entities active in a small recent scene window.
3. Include facts directly connected to selected entities.
4. Traverse at most one edge and only when the neighbouring entity is required to express the fact.
5. Prefer active hard facts over soft or proposed facts.
6. Prioritize negative constraints and predicates known to cause feature bleeding.
7. Enforce both a fact-count limit and a token/character budget.
8. Never infer `FALSE` from a missing edge.
9. Do not expand through generic social relations such as `IS_FRIEND_OF` unless the relation itself is relevant.
10. Repeat retrieval after generation using the draft response.

Retrieval must not include broad neighbourhoods. Over-retrieval could itself increase feature bleeding by placing unrelated character features next to one another in the prompt.

## 9. Failure behavior

- Graph feature disabled: preserve the existing prompt and validation flow.
- Graph file absent: initialize it from fixed protagonists; if initialization is unavailable or disabled, preserve existing behavior with an empty runtime graph.
- Graph file malformed at startup: fail with an actionable configuration error.
- Graph file becomes malformed during runtime reload: retain the last valid snapshot and report the error.
- Entity reference is ambiguous: include no entity-specific correction based solely on that alias.
- No matching fact: treat the result as unknown.
- Graph retrieval fails during a story turn: continue according to a configurable fail-open policy initially; validation itself retains its current fail-closed behavior.
- Conflicting active hard facts: reject the graph update instead of choosing one silently.

## 10. Observability and evaluation

The feature should expose enough information to determine whether it helps:

- number of entities resolved per turn;
- number of facts injected into generation and validation;
- retrieval truncation count;
- ambiguous alias count;
- graph-assisted `ALLOW` and `REPLACE` counts;
- contradictions by predicate;
- estimated prompt tokens consumed by graph facts;
- rejected graph updates and their reasons.

Debug logging must not dump full private story content by default. Stable fact and entity IDs are preferred.

Evaluation cases should include:

- explicit positive/negative feature contradictions;
- ownership versus temporary use;
- two characters with similar names;
- aliases and nicknames;
- pronouns with an active scene entity;
- a draft that introduces an entity absent from the user input;
- unknown facts that must not be treated as false;
- conflicting proposed and hard facts;
- compliance cases that must remain unchanged;
- correction cases where only the violating passage changes.

## 11. Stepwise implementation plan

### Phase 0: Establish baselines

1. Add a focused regression corpus for entity contagion and feature bleeding.
2. Record current draft and validator outcomes without graph injection.
3. Keep current recent-turn settings unchanged.

**Exit criterion:** Reproducible baseline tests and representative story scenarios exist.

### Phase 1: Closed ontology and graph core

1. Add graph configuration with an enable flag, file path and ontology definition.
2. Define the initial entity types, predicates and permitted subject/object combinations.
3. Introduce JSON domain records, candidate-operation records and enums.
4. Implement document validation and `KnowledgeGraphStore` loading.
5. Build immutable in-memory indexes.
6. Add direct lookup, positive/negative truth and alias resolution tests.
7. Provide a small manually maintained example graph.

**Exit criterion:** A valid graph is loaded and queried deterministically; no prompt behavior changes yet.

### Phase 2: Fixed-protagonist initialization and explicit `/generategraph`

1. Add schema-constrained extraction of graph operations from fixed protagonists.
2. Automatically initialize a missing graph and mark extracted facts as hard fixed-protagonist facts.
3. Implement deterministic Java validation, normalization, deduplication and reconciliation.
4. Add `/generategraph` using fixed protagonists, canonical state, summary and a configurable number of recent complete turns.
5. Retain source provenance and reject lower-authority conflicts.
6. Persist valid results atomically and retain the current snapshot when extraction or reconciliation fails.
7. Keep graph mutation behind this explicit management command; normal story turns remain read-only with respect to persisted graph state.

**Exit criterion:** A missing graph is initialized automatically, and `/generategraph` can safely synchronize it without allowing the LLM to write arbitrary ontology or graph state.

### Phase 3: Validator retrieval and correction

1. Resolve entities from user input and draft response.
2. Select a bounded set of relevant hard facts.
3. Add those facts to the validation prompt.
4. Tighten validator instructions around unknown facts, ownership and minimal rewriting.
5. Test `ALLOW` for compliant/unknown cases and `REPLACE` for explicit contradictions.

**Exit criterion:** Known Mike/Chris-style feature bleeding is corrected without rewriting compliant responses.

### Phase 4: Generation-time prevention

1. Resolve entities before the chat call.
2. Add relevant facts to the story chat prompt.
3. Keep validator retrieval as an independent second pass.
4. Measure draft contradiction rate and prompt overhead against Phase 0.

**Exit criterion:** Draft contradictions decrease without a measurable increase in unrelated fact leakage.

### Phase 5: Active scene context

1. Track active characters, items and location separately from long-lived canon.
2. Use active entities to improve retrieval of pronouns and implicit references.
3. Add temporal validity for scene-bound facts where required.

**Exit criterion:** Relevant facts are retrieved for common implicit references without unsafe guessing.

### Phase 6: Controlled per-turn graph updates

1. Extract explicit user claims before generation into a non-persisted graph overlay.
2. Use the overlay for generation and validation during the same turn.
3. Commit valid user-sourced changes only after the turn succeeds.
4. Extract candidate facts only from the final validated response, never from a rejected draft.
5. Store assistant-derived candidates as `PROPOSED` or soft until predicate-specific promotion rules exist.
6. Detect conflicts with hard facts before persistence.
7. Add explicit promotion, rejection, temporal closing and supersession policies.
8. Write accepted changes atomically and publish a new immutable snapshot.

**Exit criterion:** Runtime updates cannot silently override hard canon and remain recoverable/auditable.

### Phase 7: Context-budget tuning

1. Replace fixed recent-turn limits with token-budgeted selection if supported by the prompt assembly design.
2. Reduce recent context gradually while holding graph behavior constant.
3. Compare continuity, coreference and feature-bleeding metrics at each setting.
4. Choose defaults from evidence rather than assumed token savings.

**Exit criterion:** A lower context budget preserves or improves measured story continuity and correctness.

### Phase 8: Re-evaluate graph library needs

Reconsider JGraphT or another engine only if profiling or requirements demonstrate a need for:

- multi-hop algorithms;
- large-scale traversal;
- graph sizes that make current indexes insufficient;
- complex persistence or concurrent update patterns.

Do not introduce a graph framework solely because the domain is represented as nodes and edges.

## 12. Initial code integration points

Expected integration points in the current codebase:

- `AppConfig`: expose an immutable graph-specific configuration record containing the enable flag, graph path, recent-turn count and prompt budgets. Graph classes should not receive the complete application configuration.
- `ApplicationFactory`: construct and wire the graph facade. Consumers should receive the facade, not the store, reconciler or extraction implementation.
- `TerminalStoryteller`: recognize `/generategraph` as a control command, call the graph management facade and report its result without adding it as a story turn.
- `PromptResourceLoader`: load the configured ontology and graph-extraction prompt for the graph extraction adapter.
- `PromptAssemblyService` / `StoryChatPromptBuilder`: request generation-time relevant facts through the graph facade.
- `ValidationPromptBuilder`: request validator-time relevant hard facts through the graph facade.
- `StorySessionService`: ensure the draft is included in the second retrieval and only the accepted response reaches history.
- `ResponseGuard`: retain the current `ALLOW` / `REPLACE` behavior; optionally accept structured graph evidence as input.
- `HistoryStore` or a dedicated scene store: later hold active scene entity IDs, not the full graph.

The graph should remain a separate bounded context rather than being folded into `CanonicalStateManager`. Canonical prose is useful for narrative summarization; the graph provides typed, deterministic facts. One may help derive the other, but they have different responsibilities and failure modes.

The intended dependency direction is:

```text
CLI adapter -----------------------> KnowledgeGraphService
Prompt/validation consumers ------> KnowledgeGraphService
ApplicationFactory ---------------> graph construction and wiring

KnowledgeGraphService ------------> graph domain, generation and persistence
graph generation adapter ---------> generic ChatClient port

core.graph -/-> CLI, terminal rendering or story orchestration
```

Package naming alone does not enforce these boundaries. Constructor dependencies and tests should keep graph internals inaccessible to consumers wherever practical.

## 13. Open decisions

Before implementing controlled per-turn graph updates, decide:

1. Which evidence is sufficient to close or supersede an existing `POSSESSES` fact?
2. Which predicate and entity type should be added after the initial ownership/skill slice, based on observed failures?
3. May story-generated facts ever become hard automatically?
4. How are user corrections expressed and promoted?
5. Should file changes be reloaded automatically or only at startup/reset?
6. What fact and token budgets apply to generation and validation?
7. How should a user inspect proposed and conflicting facts?
8. Is source provenance based on turn numbers sufficient, or must it reference exact message IDs?
9. Should a future rebuild mode preserve manually sourced facts automatically or require an explicit confirmation/diff workflow?

These decisions should be made from concrete story scenarios and regression tests rather than by attempting to model a general-purpose ontology up front.

## 14. Recommended first delivery

The smallest production-safe delivery is:

1. versioned JSON on disk;
2. a closed ontology containing only `CHARACTER`, `ITEM`, `SKILL`, `POSSESSES` and `CAN_PERFORM`;
3. immutable Java indexes and deterministic reconciliation;
4. automatic initialization from fixed protagonists when the graph is absent;
5. `/generategraph` synchronization from fixed protagonists, canonical state, summary and recent complete turns;
6. exact name and alias matching;
7. validator-time retrieval from user input plus draft response;
8. minimal `REPLACE` corrections through the existing validator contract;
9. graph mutation only through initialization and the explicit `/generategraph` management command, not during normal story turns;
10. no change to the recent-turn count used by normal story generation.

This first delivery deliberately permits LLM-assisted extraction only behind initialization and the explicit `/generategraph` command. Java remains authoritative for graph validity and persistence. Generation-time injection and automatic per-turn mutation should be added and measured as separate increments; lower recent-context budgets should follow only after retrieval has demonstrated that it preserves continuity and reduces the target errors.
